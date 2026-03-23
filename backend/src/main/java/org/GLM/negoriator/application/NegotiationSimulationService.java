package org.GLM.negoriator.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.GLM.negoriator.ai.AiGatewayService;
import org.GLM.negoriator.domain.NegotiationSession;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NegotiationSimulationService {

	private static final Logger log = LoggerFactory.getLogger(NegotiationSimulationService.class);

	private final NegotiationApplicationService negotiationService;
	private final AiGatewayService aiGateway;
	private final ObjectMapper objectMapper;

	public NegotiationSimulationService(
		NegotiationApplicationService negotiationService,
		AiGatewayService aiGateway,
		ObjectMapper objectMapper
	) {
		this.negotiationService = negotiationService;
		this.aiGateway = aiGateway;
		this.objectMapper = objectMapper;
	}

	@Transactional
	public SimulationResult runSimulation(SimulationConfig config) {
		NegotiationSession session = negotiationService.startSession(
			NegotiationDefaults.startSessionCommand(config.strategy()));

		UUID sessionId = session.getId();
		NegotiationBounds bounds = NegotiationDefaults.bounds();
		List<SimulationRound> rounds = new ArrayList<>();
		String conversationContext = buildInitialContext(bounds, config);

		log.info("Simulation started: session={}, strategy={}, maxRounds={}",
			sessionId, config.strategy(), session.getMaxRounds());

		for (int round = 1; round <= session.getMaxRounds(); round++) {
			session = negotiationService.getSession(sessionId);

			if (session.isClosed()) {
				log.info("Simulation ended early at round {} (status={})", round, session.getStatus());
				break;
			}

			String supplierMessage;
			OfferVector supplierOffer;

			try {
				supplierMessage = generateSupplierMessage(conversationContext, round, bounds);
				supplierOffer = parseOfferFromMessage(supplierMessage, bounds);
			} catch (Exception e) {
				log.warn("Round {}: AI supplier failed to generate valid offer: {}", round, e.getMessage());
				rounds.add(new SimulationRound(round, "AI_ERROR: " + e.getMessage(), null, null, null));
				break;
			}

			log.info("Round {}: Supplier says: \"{}\" → parsed as price={}, payment={}d, delivery={}d, contract={}m",
				round, supplierMessage, supplierOffer.price(), supplierOffer.paymentDays(),
				supplierOffer.deliveryDays(), supplierOffer.contractMonths());

			try {
				session = negotiationService.submitSupplierOffer(sessionId, supplierOffer, null, supplierMessage);
			} catch (Exception e) {
				log.error("Round {}: Engine error: {}", round, e.getMessage());
				rounds.add(new SimulationRound(round, supplierMessage, supplierOffer, null, "ENGINE_ERROR: " + e.getMessage()));
				break;
			}

			String status = session.getStatus().name();
			String strategy = session.getStrategy().name();
			BuyerRoundSummary buyerSummary = extractBuyerSummary(session, round);

			rounds.add(new SimulationRound(round, supplierMessage, supplierOffer, buyerSummary, null));

			log.info("Round {}: Buyer decision={}, status={}, strategy={}", round, buyerSummary.decision(), status, strategy);

			if (buyerSummary.counterOffers() != null) {
				for (int i = 0; i < buyerSummary.counterOffers().size(); i++) {
					log.info("  Counter-offer {}: {}", i + 1, buyerSummary.counterOffers().get(i));
				}
			}

			conversationContext = updateConversation(conversationContext, round, supplierMessage, buyerSummary);

			if (session.isClosed()) {
				log.info("Simulation complete: final status={}", status);
				break;
			}
		}

		session = negotiationService.getSession(sessionId);
		SimulationResult result = new SimulationResult(
			sessionId,
			session.getStatus().name(),
			session.getStrategy().name(),
			session.getCurrentRound(),
			rounds,
			detectAnomalies(rounds, session));

		log.info("Simulation result: status={}, rounds={}, anomalies={}",
			result.finalStatus(), result.roundsPlayed(), result.anomalies().size());
		for (String anomaly : result.anomalies()) {
			log.warn("  ANOMALY: {}", anomaly);
		}

		return result;
	}

	private String buildInitialContext(NegotiationBounds bounds, SimulationConfig config) {
		return String.format("""
			You are a supplier negotiating a procurement contract with a buyer agent.
			You want to maximize your profit. Negotiate naturally in plain English.

			Negotiation bounds:
			- Price: %.2f to %.2f EUR
			- Payment: %d to %d days
			- Delivery: %d to %d days
			- Contract: %d to %d months

			Your goals as supplier:
			- Get the highest price possible (ideally above %.2f)
			- Get shortest payment terms (ideally under %d days)
			- Delivery flexibility is okay
			- Prefer shorter contracts

			Personality: %s

			IMPORTANT: Each message MUST include specific numbers for price, payment days, delivery days, and contract months.
			Format example: "I propose price 105, payment in 45 days, delivery in 7 days, and a 12 month contract."

			The negotiation is about to begin. The buyer will respond with counter-offers.
			""",
			bounds.minPrice().doubleValue(), bounds.maxPrice().doubleValue(),
			bounds.minPaymentDays(), bounds.maxPaymentDays(),
			bounds.minDeliveryDays(), bounds.maxDeliveryDays(),
			bounds.minContractMonths(), bounds.maxContractMonths(),
			bounds.maxPrice().doubleValue() * 0.85,
			bounds.minPaymentDays() + 10,
			config.supplierPersonality());
	}

	private String generateSupplierMessage(String context, int round, NegotiationBounds bounds) {
		String userPrompt = round == 1
			? "Send your opening proposal. Include specific numbers for all four terms."
			: "Continue the negotiation based on the buyer's latest response. Include specific numbers for all four terms (price, payment days, delivery days, contract months).";

		String response = aiGateway.complete(context, userPrompt);
		return response.trim();
	}

	private OfferVector parseOfferFromMessage(String message, NegotiationBounds bounds) {
		String parsePrompt = """
			Extract the negotiation terms from this supplier message.
			Return ONLY valid JSON with these exact keys: price, paymentDays, deliveryDays, contractMonths.
			All values must be numbers. No markdown fences. No explanation.
			""";

		String response = aiGateway.complete(parsePrompt, message);
		JsonNode json;

		try {
			String cleaned = response.trim();
			int start = cleaned.indexOf('{');
			int end = cleaned.lastIndexOf('}');
			if (start < 0 || end <= start) {
				throw new IllegalArgumentException("No JSON object in AI response: " + cleaned);
			}
			json = objectMapper.readTree(cleaned.substring(start, end + 1));
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("Failed to parse AI response as JSON: " + response, e);
		}

		BigDecimal price = json.has("price") ? BigDecimal.valueOf(json.get("price").asDouble()) : null;
		Integer paymentDays = json.has("paymentDays") ? json.get("paymentDays").asInt() : null;
		Integer deliveryDays = json.has("deliveryDays") ? json.get("deliveryDays").asInt() : null;
		Integer contractMonths = json.has("contractMonths") ? json.get("contractMonths").asInt() : null;

		if (price == null || paymentDays == null || deliveryDays == null || contractMonths == null) {
			throw new IllegalArgumentException("AI response missing fields: " + response);
		}

		price = price.max(bounds.minPrice()).min(bounds.maxPrice());
		paymentDays = Math.max(bounds.minPaymentDays(), Math.min(bounds.maxPaymentDays(), paymentDays));
		deliveryDays = Math.max(bounds.minDeliveryDays(), Math.min(bounds.maxDeliveryDays(), deliveryDays));
		contractMonths = Math.max(bounds.minContractMonths(), Math.min(bounds.maxContractMonths(), contractMonths));

		return new OfferVector(price, paymentDays, deliveryDays, contractMonths);
	}

	private String updateConversation(String context, int round, String supplierMessage, BuyerRoundSummary buyer) {
		StringBuilder sb = new StringBuilder(context);
		sb.append("\n\n--- Round ").append(round).append(" ---\n");
		sb.append("You said: \"").append(supplierMessage).append("\"\n");
		sb.append("Buyer decision: ").append(buyer.decision()).append("\n");

		if (buyer.explanation() != null) {
			sb.append("Buyer explanation: ").append(buyer.explanation()).append("\n");
		}
		if (buyer.counterOffers() != null && !buyer.counterOffers().isEmpty()) {
			sb.append("Buyer counter-offers:\n");
			for (int i = 0; i < buyer.counterOffers().size(); i++) {
				OfferVector co = buyer.counterOffers().get(i);
				sb.append("  Option ").append(i + 1).append(": price=").append(co.price())
					.append(", payment=").append(co.paymentDays()).append("d")
					.append(", delivery=").append(co.deliveryDays()).append("d")
					.append(", contract=").append(co.contractMonths()).append("m\n");
			}
		}

		return sb.toString();
	}

	private BuyerRoundSummary extractBuyerSummary(NegotiationSession session, int round) {
		var decision = session.getDecisions().stream()
			.filter(d -> d.getRoundNumber() == round)
			.findFirst()
			.orElse(null);

		if (decision == null) {
			return new BuyerRoundSummary("UNKNOWN", null, null, List.of());
		}

		var counterOffers = session.getOffers().stream()
			.filter(o -> o.getRoundNumber() == round)
			.filter(o -> o.getParty() == org.GLM.negoriator.domain.NegotiationParty.BUYER)
			.map(o -> o.toOfferVector())
			.toList();

		return new BuyerRoundSummary(
			decision.getDecision().name(),
			decision.getExplanation(),
			decision.toOfferEvaluation(),
			counterOffers);
	}

	private List<String> detectAnomalies(List<SimulationRound> rounds, NegotiationSession session) {
		List<String> anomalies = new ArrayList<>();

		for (SimulationRound round : rounds) {
			if (round.error() != null) {
				anomalies.add("Round " + round.round() + ": " + round.error());
				continue;
			}

			if (round.buyer() == null) {
				continue;
			}

			if ("ACCEPT".equals(round.buyer().decision()) && round.buyer().evaluation() != null) {
				BigDecimal buyerUtility = round.buyer().evaluation().buyerUtility();
				BigDecimal reservationUtility = NegotiationDefaults.buyerProfile().reservationUtility();

				if (buyerUtility.compareTo(reservationUtility) < 0) {
					anomalies.add(String.format(
						"Round %d: ACCEPTED with buyer utility %.4f below reservation %.4f",
						round.round(), buyerUtility, reservationUtility));
				}
			}

			if ("ACCEPT".equals(round.buyer().decision()) && round.round() == 1) {
				anomalies.add("Round 1: Suspicious first-round acceptance");
			}

			if ("REJECT".equals(round.buyer().decision()) && round.buyer().evaluation() != null) {
				BigDecimal buyerUtility = round.buyer().evaluation().buyerUtility();
				if (buyerUtility.compareTo(new BigDecimal("0.8")) > 0) {
					anomalies.add(String.format(
						"Round %d: REJECTED with high buyer utility %.4f — possible missed deal",
						round.round(), buyerUtility));
				}
			}
		}

		if (session.isClosed() && "ACCEPTED".equals(session.getStatus().name())) {
			var lastDecision = session.getDecisions().stream()
				.max(java.util.Comparator.comparing(d -> d.getRoundNumber()))
				.orElse(null);
			if (lastDecision != null && lastDecision.getExplanation() != null
				&& lastDecision.getExplanation().contains("agreed to the buyer's active offer")) {
				anomalies.add("Acceptance via offer-match shortcut — verify this was intended");
			}
		}

		return anomalies;
	}

	public record SimulationConfig(
		org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy strategy,
		String supplierPersonality
	) {
		public static SimulationConfig of(
			org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy strategy,
			String supplierPersonality
		) {
			return new SimulationConfig(
				strategy != null ? strategy : NegotiationDefaults.defaultStrategy(),
				supplierPersonality != null && !supplierPersonality.isBlank()
					? supplierPersonality
					: "Professional but firm. You make reasonable concessions but push back on aggressive buyer positions."
			);
		}
	}

	public record SimulationResult(
		UUID sessionId,
		String finalStatus,
		String finalStrategy,
		int roundsPlayed,
		List<SimulationRound> rounds,
		List<String> anomalies
	) {
	}

	public record SimulationRound(
		int round,
		String supplierMessage,
		OfferVector supplierOffer,
		BuyerRoundSummary buyer,
		String error
	) {
	}

	public record BuyerRoundSummary(
		String decision,
		String explanation,
		org.GLM.negoriator.negotiation.NegotiationEngine.OfferEvaluation evaluation,
		List<OfferVector> counterOffers
	) {
	}
}
