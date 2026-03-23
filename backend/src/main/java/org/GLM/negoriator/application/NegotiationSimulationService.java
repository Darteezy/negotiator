package org.GLM.negoriator.application;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Arrays;
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
	// Each pattern has two alternatives: keyword-then-number and number-then-keyword.
	// matchInt() returns the first non-null capturing group.
	private static final Pattern PRICE_PATTERN = Pattern.compile(
		"(?:price(?:[_ ]?eur)?)\\D{0,20}(\\d+(?:\\.\\d+)?)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern PAYMENT_PATTERN = Pattern.compile(
		"(?:payment(?:[_ ]?days)?|pay(?:ment)?(?:\\s*in)?)\\D{0,20}(\\d{1,3})" +
		"|(\\d{1,3})\\s*-?\\s*days?\\s*(?:payment|pay(?:\\s*term)?)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern DELIVERY_PATTERN = Pattern.compile(
		"(?:delivery(?:[_ ]?days)?|lead\\s*time|deliver(?:y)?(?:\\s*in)?)\\D{0,20}(\\d{1,3})" +
		"|(\\d{1,3})\\s*-?\\s*days?\\s*(?:delivery|deliver|lead)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern CONTRACT_PATTERN = Pattern.compile(
		"(?:contract(?:[_ ]?months)?|commitment)\\D{0,20}(\\d{1,3})" +
		"|(\\d{1,3})\\s*-?\\s*months?\\s*(?:contract|term|commitment)",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern FULL_PROPOSAL_PATTERN = Pattern.compile(
		"(?is)price\\D{0,20}\\d+(?:\\.\\d+)?[^\\n\\r]{0,220}?payment[^\\n\\r]{0,80}?\\d{1,3}[^\\n\\r]{0,220}?delivery[^\\n\\r]{0,80}?\\d{1,3}[^\\n\\r]{0,220}?(?:contract|term|commitment)[^\\n\\r]{0,80}?\\d{1,3}");

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
		return runSimulation(config, SimulationListener.NO_OP);
	}

	@Transactional
	public SimulationResult runSimulation(SimulationConfig config, SimulationListener listener) {
		NegotiationSession session = negotiationService.startSession(
			startSessionCommand(config));

		UUID sessionId = session.getId();
		NegotiationBounds bounds = NegotiationDefaults.bounds();
		List<SimulationRound> rounds = new ArrayList<>();
		String conversationContext = buildInitialContext(bounds, config);

		log.info("Simulation started: session={}, strategy={}, maxRounds={}",
			sessionId, config.strategy(), session.getMaxRounds());
		listener.onStarted(session);

		for (int round = 1; round <= session.getMaxRounds(); round++) {
			session = negotiationService.getSession(sessionId);

			if (session.isClosed()) {
				log.info("Simulation ended early at round {} (status={})", round, session.getStatus());
				break;
			}

			SupplierTurn supplierTurn;

			try {
				supplierTurn = generateSupplierTurn(conversationContext, round, bounds);
			} catch (Exception e) {
				log.warn("Round {}: AI supplier failed to generate valid offer: {}", round, e.getMessage());
				SimulationRound failedRound = new SimulationRound(round, "AI_ERROR: " + e.getMessage(), null, null, null);
				rounds.add(failedRound);
				listener.onRound(negotiationService.getSession(sessionId), failedRound);
				break;
			}

			String supplierMessage = supplierTurn.message();
			OfferVector supplierOffer = supplierTurn.offer();

			log.info("Round {}: Supplier says: \"{}\" → parsed as price={}, payment={}d, delivery={}d, contract={}m",
				round, supplierMessage, supplierOffer.price(), supplierOffer.paymentDays(),
				supplierOffer.deliveryDays(), supplierOffer.contractMonths());

			try {
				session = negotiationService.submitSupplierOffer(sessionId, supplierOffer, null, supplierMessage);
			} catch (Exception e) {
				log.error("Round {}: Engine error: {}", round, e.getMessage());
				SimulationRound failedRound = new SimulationRound(round, supplierMessage, supplierOffer, null, "ENGINE_ERROR: " + e.getMessage());
				rounds.add(failedRound);
				listener.onRound(negotiationService.getSession(sessionId), failedRound);
				break;
			}

			String status = session.getStatus().name();
			String strategy = session.getStrategy().name();
			BuyerRoundSummary buyerSummary = extractBuyerSummary(session, round);

			SimulationRound completedRound = new SimulationRound(round, supplierMessage, supplierOffer, buyerSummary, null);
			rounds.add(completedRound);
			listener.onRound(session, completedRound);

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
		listener.onCompleted(session, result);

		return result;
	}

	private NegotiationApplicationService.StartSessionCommand startSessionCommand(SimulationConfig config) {
		return new NegotiationApplicationService.StartSessionCommand(
			config.strategy(),
			config.maxRounds(),
			NegotiationDefaults.riskOfWalkaway(),
			NegotiationDefaults.buyerProfile(),
			NegotiationDefaults.bounds(),
			NegotiationDefaults.supplierModel());
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

			IMPORTANT:
			- Each message MUST include specific numbers for price, payment days, delivery days, and contract months.
			- Return one short supplier reply only.
			- Do NOT include reasoning, markdown, tables, bullet lists, or roleplay sections.
			- Do NOT quote the buyer's previous offer unless you are explicitly accepting it.
			- Format example: "I propose price 105, payment in 45 days, delivery in 7 days, and a 12 month contract."

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

	private SupplierTurn generateSupplierTurn(String context, int round, NegotiationBounds bounds) {
		String userPrompt = round == 1
			? "Send your opening supplier proposal as one short reply. Include explicit numeric values for price, payment days, delivery days, and contract months. No reasoning, no markdown, no tables."
			: "Continue the negotiation as one short supplier reply. Include explicit numeric values for price, payment days, delivery days, and contract months. No reasoning, no markdown, no tables.";

		String response = aiGateway.complete(context, userPrompt);
		String cleaned = response.trim();

		ParsedTerms parsedTerms = parseSupplierTerms(cleaned);
		String message = parsedTerms.message();
		BigDecimal price = parsedTerms.price();
		Integer paymentDays = parsedTerms.paymentDays();
		Integer deliveryDays = parsedTerms.deliveryDays();
		Integer contractMonths = parsedTerms.contractMonths();

		if (message == null || message.isBlank() || price == null || paymentDays == null || deliveryDays == null || contractMonths == null) {
			throw new IllegalArgumentException("AI response missing fields: " + response);
		}

		price = price.max(bounds.minPrice()).min(bounds.maxPrice());
		paymentDays = Math.max(bounds.minPaymentDays(), Math.min(bounds.maxPaymentDays(), paymentDays));
		deliveryDays = Math.max(bounds.minDeliveryDays(), Math.min(bounds.maxDeliveryDays(), deliveryDays));
		contractMonths = Math.max(bounds.minContractMonths(), Math.min(bounds.maxContractMonths(), contractMonths));

		return new SupplierTurn(
			message.trim(),
			new OfferVector(price, paymentDays, deliveryDays, contractMonths));
	}

	private ParsedTerms parseSupplierTerms(String rawResponse) {
		JsonNode json = tryReadJsonObject(rawResponse);
		if (json != null) {
			JsonNode termsNode = extractTermsNode(json);
			String message = extractMessage(rawResponse, json, termsNode);
			BigDecimal price = decimalField(termsNode, "price", "price_eur");
			Integer paymentDays = intField(termsNode, "paymentDays", "payment_days", "payment");
			Integer deliveryDays = intField(termsNode, "deliveryDays", "delivery_days", "delivery");
			Integer contractMonths = intField(termsNode, "contractMonths", "contract_months", "contract");

			if (message != null && price != null && paymentDays != null && deliveryDays != null && contractMonths != null) {
				return new ParsedTerms(message, price, paymentDays, deliveryDays, contractMonths);
			}
		}

		String proposalSegment = extractProposalSegment(rawResponse);
		BigDecimal price = matchDecimal(proposalSegment, PRICE_PATTERN);
		Integer paymentDays = matchInt(proposalSegment, PAYMENT_PATTERN);
		Integer deliveryDays = matchInt(proposalSegment, DELIVERY_PATTERN);
		Integer contractMonths = matchInt(proposalSegment, CONTRACT_PATTERN);

		if (price == null || paymentDays == null || deliveryDays == null || contractMonths == null) {
			price = matchDecimal(rawResponse, PRICE_PATTERN);
			paymentDays = matchInt(rawResponse, PAYMENT_PATTERN);
			deliveryDays = matchInt(rawResponse, DELIVERY_PATTERN);
			contractMonths = matchInt(rawResponse, CONTRACT_PATTERN);
		}

		if (price == null || paymentDays == null || deliveryDays == null || contractMonths == null) {
			throw new IllegalArgumentException("AI response missing fields: " + rawResponse);
		}

		return new ParsedTerms(rawResponse.trim(), price, paymentDays, deliveryDays, contractMonths);
	}

	private JsonNode tryReadJsonObject(String rawResponse) {
		String cleaned = rawResponse == null ? "" : rawResponse.trim();
		int start = cleaned.indexOf('{');
		int end = cleaned.lastIndexOf('}');
		if (start < 0 || end <= start) {
			return null;
		}

		try {
			return objectMapper.readTree(cleaned.substring(start, end + 1));
		} catch (JsonProcessingException e) {
			return null;
		}
	}

	private JsonNode extractTermsNode(JsonNode json) {
		if (json.has("proposal") && json.get("proposal").isObject()) {
			return json.get("proposal");
		}

		if (json.has("supplier_response") && json.get("supplier_response").isObject()) {
			JsonNode supplierResponse = json.get("supplier_response");
			if (supplierResponse.has("proposal") && supplierResponse.get("proposal").isObject()) {
				return supplierResponse.get("proposal");
			}
			return supplierResponse;
		}

		return json;
	}

	private String extractMessage(String rawResponse, JsonNode json, JsonNode termsNode) {
		String directMessage = textField(json, "message");
		if (directMessage != null && !directMessage.isBlank()) {
			return directMessage;
		}

		if (json.has("supplier_response") && json.get("supplier_response").isObject()) {
			String nestedMessage = textField(json.get("supplier_response"), "message");
			if (nestedMessage != null && !nestedMessage.isBlank()) {
				return nestedMessage;
			}
		}

		String trimmed = rawResponse == null ? "" : rawResponse.trim();
		int jsonStart = trimmed.indexOf('{');
		if (jsonStart > 0) {
			String prefix = trimmed.substring(0, jsonStart).trim();
			if (!prefix.isBlank()) {
				return prefix.replace("```json", "").replace("```", "").trim();
			}
		}

		BigDecimal price = decimalField(termsNode, "price", "price_eur");
		Integer paymentDays = intField(termsNode, "paymentDays", "payment_days", "payment");
		Integer deliveryDays = intField(termsNode, "deliveryDays", "delivery_days", "delivery");
		Integer contractMonths = intField(termsNode, "contractMonths", "contract_months", "contract");

		if (price != null && paymentDays != null && deliveryDays != null && contractMonths != null) {
			return "I propose price " + price
				+ ", payment in " + paymentDays + " days, delivery in " + deliveryDays
				+ " days, and a " + contractMonths + " month contract.";
		}

		return null;
	}

	private String textField(JsonNode node, String... names) {
		for (String name : names) {
			if (node.has(name) && !node.get(name).isNull()) {
				return node.get(name).asText();
			}
		}
		return null;
	}

	private Integer intField(JsonNode node, String... names) {
		for (String name : names) {
			if (node.has(name) && node.get(name).isNumber()) {
				return node.get(name).asInt();
			}
		}
		return null;
	}

	private BigDecimal decimalField(JsonNode node, String... names) {
		for (String name : names) {
			if (node.has(name) && node.get(name).isNumber()) {
				return BigDecimal.valueOf(node.get(name).asDouble());
			}
		}
		return null;
	}

	private BigDecimal matchDecimal(String text, Pattern pattern) {
		Matcher matcher = pattern.matcher(text);
		if (!matcher.find()) {
			return null;
		}
		for (int i = 1; i <= matcher.groupCount(); i++) {
			String g = matcher.group(i);
			if (g != null) {
				return new BigDecimal(g);
			}
		}
		return null;
	}

	private Integer matchInt(String text, Pattern pattern) {
		Matcher matcher = pattern.matcher(text);
		if (!matcher.find()) {
			return null;
		}
		for (int i = 1; i <= matcher.groupCount(); i++) {
			String g = matcher.group(i);
			if (g != null) {
				return Integer.parseInt(g);
			}
		}
		return null;
	}

	private String extractProposalSegment(String rawResponse) {
		String sanitized = sanitizeSupplierResponse(rawResponse);

		for (String paragraph : Arrays.stream(sanitized.split("(?:\\n\\s*\\n)+")).map(String::trim).toList()) {
			if (looksLikeProposalParagraph(paragraph)) {
				return paragraph;
			}
		}

		Matcher matcher = FULL_PROPOSAL_PATTERN.matcher(sanitized);
		if (matcher.find()) {
			return matcher.group().trim();
		}

		return sanitized;
	}

	private String sanitizeSupplierResponse(String rawResponse) {
		if (rawResponse == null) {
			return "";
		}

		StringBuilder sanitized = new StringBuilder();
		for (String line : rawResponse.replace("```", "").replace("**", "").split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isBlank()) {
				sanitized.append("\n\n");
				continue;
			}
			if (trimmed.startsWith("|") || trimmed.startsWith("- ") || trimmed.startsWith("You said:")) {
				continue;
			}
			if (trimmed.equalsIgnoreCase("My Response:")
				|| trimmed.equalsIgnoreCase("Reasoning:")
				|| trimmed.equalsIgnoreCase("Negotiation Logic:")) {
				continue;
			}
			sanitized.append(trimmed).append('\n');
		}
		return sanitized.toString().trim();
	}

	private boolean looksLikeProposalParagraph(String text) {
		String lower = text.toLowerCase();
		return lower.contains("price")
			&& lower.contains("payment")
			&& lower.contains("delivery")
			&& (lower.contains("contract") || lower.contains("term") || lower.contains("commitment"));
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
			return new BuyerRoundSummary("UNKNOWN", null, null, List.of(), null, null, null);
		}

		var strategyChange = session.getStrategyChanges().stream()
			.filter(change -> change.getRoundNumber() == round)
			.filter(change -> change.getPreviousStrategy() != null)
			.max(java.util.Comparator.comparing(change -> change.getCreatedAt(), java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
			.orElse(null);

		var counterOffers = session.getOffers().stream()
			.filter(o -> o.getRoundNumber() == round)
			.filter(o -> o.getParty() == org.GLM.negoriator.domain.NegotiationParty.BUYER)
			.map(o -> o.toOfferVector())
			.toList();

		return new BuyerRoundSummary(
			decision.getDecision().name(),
			decision.getExplanation(),
			decision.toOfferEvaluation(),
			counterOffers,
			decision.getStrategyUsed() != null ? decision.getStrategyUsed().name() : null,
			strategyChange != null ? strategyChange.getRationale() : decision.getStrategyRationale(),
			strategyChange != null && strategyChange.getTrigger() != null ? strategyChange.getTrigger().name() : null);
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

			if (round.supplierOffer() != null && round.buyer().counterOffers() != null) {
				for (OfferVector counterOffer : round.buyer().counterOffers()) {
					if (counterOffer.price().compareTo(round.supplierOffer().price()) > 0
						|| counterOffer.paymentDays() < round.supplierOffer().paymentDays()
						|| counterOffer.deliveryDays() > round.supplierOffer().deliveryDays()
						|| counterOffer.contractMonths() > round.supplierOffer().contractMonths()) {
						anomalies.add(String.format(
							"Round %d: Buyer counteroffer is worse for the buyer than the supplier offer (%s vs %s)",
							round.round(), counterOffer, round.supplierOffer()));
					}
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
		String supplierPersonality,
		int maxRounds
	) {
		public static SimulationConfig of(
			org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy strategy,
			String supplierPersonality,
			Integer maxRounds
		) {
			return new SimulationConfig(
				strategy != null ? strategy : NegotiationDefaults.defaultStrategy(),
				supplierPersonality != null && !supplierPersonality.isBlank()
					? supplierPersonality
					: "Professional but firm. You make reasonable concessions but push back on aggressive buyer positions.",
				maxRounds != null && maxRounds > 0
					? maxRounds
					: Math.min(4, NegotiationDefaults.maxRounds(strategy != null ? strategy : NegotiationDefaults.defaultStrategy()))
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
		List<OfferVector> counterOffers,
		String strategyUsed,
		String strategyRationale,
		String switchTrigger
	) {
	}

	public interface SimulationListener {
		SimulationListener NO_OP = new SimulationListener() {
		};

		default void onStarted(NegotiationSession session) {
		}

		default void onRound(NegotiationSession session, SimulationRound round) {
		}

		default void onCompleted(NegotiationSession session, SimulationResult result) {
		}
	}

	private record SupplierTurn(
		String message,
		OfferVector offer
	) {
	}

	private record ParsedTerms(
		String message,
		BigDecimal price,
		Integer paymentDays,
		Integer deliveryDays,
		Integer contractMonths
	) {
	}
}
