package org.GLM.negoriator.ai;

import java.util.Locale;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.GLM.negoriator.domain.NegotiationOffer;
import org.GLM.negoriator.domain.NegotiationParty;
import org.GLM.negoriator.domain.NegotiationSession;
import org.GLM.negoriator.domain.NegotiationStrategyChangeTrigger;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferEvaluation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiStrategyAdvisor {

	private static final Logger log = LoggerFactory.getLogger(AiStrategyAdvisor.class);

	private final AiGatewayService aiGateway;
	private final ObjectMapper objectMapper;

	public AiStrategyAdvisor(AiGatewayService aiGateway, ObjectMapper objectMapper) {
		this.aiGateway = aiGateway;
		this.objectMapper = objectMapper;
	}

	public StrategyAdvice advise(NegotiationSession session, OfferEvaluation evaluation) {
		if (session.getStrategy() != NegotiationStrategy.MESO || session.getCurrentRound() < 4) {
			return StrategyAdvice.none();
		}

		try {
			String systemPrompt = buildSystemPrompt();
			String userPrompt = buildUserPrompt(session, evaluation);
			String response = aiGateway.complete(systemPrompt, userPrompt);
			return parseAdvice(response, session.getStrategy());
		} catch (Exception e) {
			log.warn("AI strategy advisor failed, keeping current strategy: {}", e.getMessage());
			return StrategyAdvice.none();
		}
	}

	private String buildSystemPrompt() {
		return """
			You are a negotiation strategy advisor for an automated buyer agent. \
			You analyze conversation flow and recommend whether to switch strategies.

			Available strategies:
			- MESO: Explore supplier preferences through multiple equivalent options. Good early in negotiation.
			- BOULWARE: Hold firm on demands, concede slowly. Good when buyer has strong position.
			- CONCEDER: Soften faster to improve close probability. Good under time pressure.
			- TIT_FOR_TAT: Mirror supplier concessions directly. Good when supplier is making moves.
			- BASELINE: Linear concession policy. Default fallback.

			Respond with JSON only. No markdown fencing. Two keys:
			- "strategy": one of MESO, BOULWARE, CONCEDER, TIT_FOR_TAT, BASELINE, or "KEEP" if no switch needed
			- "rationale": one sentence explaining why

			Use TIT_FOR_TAT exactly with underscores if you recommend that strategy.
			""";
	}

	private String buildUserPrompt(NegotiationSession session, OfferEvaluation evaluation) {
		StringBuilder sb = new StringBuilder();
		sb.append("Current strategy: ").append(session.getStrategy().name()).append("\n");
		sb.append("Round: ").append(session.getCurrentRound()).append("/").append(session.getMaxRounds()).append("\n");
		sb.append("Buyer utility of latest offer: ").append(evaluation.buyerUtility()).append("\n");
		sb.append("Target utility: ").append(evaluation.targetUtility()).append("\n\n");
		sb.append("Conversation history:\n");

		List<NegotiationOffer> offers = session.getOffers().stream()
			.sorted(java.util.Comparator.comparing(NegotiationOffer::getRoundNumber)
				.thenComparing(NegotiationOffer::getCreatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
			.toList();

		for (NegotiationOffer offer : offers) {
			String party = offer.getParty() == NegotiationParty.SUPPLIER ? "Supplier" : "Buyer";
			sb.append("Round ").append(offer.getRoundNumber()).append(" - ").append(party).append(": ");

			if (offer.getParty() == NegotiationParty.SUPPLIER && offer.getSupplierMessage() != null) {
				sb.append("\"").append(offer.getSupplierMessage()).append("\" → ");
			}

			sb.append("price=").append(offer.getTerms().getPrice())
				.append(", payment=").append(offer.getTerms().getPaymentDays()).append("d")
				.append(", delivery=").append(offer.getTerms().getDeliveryDays()).append("d")
				.append(", contract=").append(offer.getTerms().getContractMonths()).append("m")
				.append("\n");
		}

		sb.append("\nShould the buyer switch strategy? Respond with JSON.");
		return sb.toString();
	}

	private StrategyAdvice parseAdvice(String response, NegotiationStrategy currentStrategy) {
		try {
			JsonNode payload = objectMapper.readTree(extractJsonObject(response));
			String recommended = normalizeStrategy(payload.path("strategy").asText());

			if (recommended == null || "KEEP".equals(recommended)) {
				return StrategyAdvice.none();
			}

			NegotiationStrategy nextStrategy = NegotiationStrategy.valueOf(recommended);
			if (nextStrategy == currentStrategy) {
				return StrategyAdvice.none();
			}

			String rationale = payload.path("rationale").asText();
			if (rationale == null || rationale.isBlank()) {
				rationale = "AI recommended switching to " + nextStrategy.name();
			}

			return new StrategyAdvice(true, nextStrategy, NegotiationStrategyChangeTrigger.AI_RECOMMENDATION, rationale);
		} catch (Exception e) {
			log.warn("Could not parse AI strategy advice '{}': {}", response, e.getMessage());
			return StrategyAdvice.none();
		}
	}

	private String extractJsonObject(String response) {
		String trimmed = response == null ? "" : response.trim();
		int start = trimmed.indexOf('{');
		int end = trimmed.lastIndexOf('}');

		if (start < 0 || end <= start) {
			throw new IllegalArgumentException("AI response did not contain a JSON object");
		}

		return trimmed.substring(start, end + 1);
	}

	private String normalizeStrategy(String rawStrategy) {
		if (rawStrategy == null || rawStrategy.isBlank()) {
			return null;
		}

		String normalized = rawStrategy.trim()
			.toUpperCase(Locale.ROOT)
			.replace('-', '_')
			.replace(' ', '_');

		if ("TIT_FOR_TAT".equals(normalized)
			|| "MESO".equals(normalized)
			|| "BOULWARE".equals(normalized)
			|| "CONCEDER".equals(normalized)
			|| "BASELINE".equals(normalized)
			|| "KEEP".equals(normalized)) {
			return normalized;
		}

		return null;
	}

	public record StrategyAdvice(
		boolean switched,
		NegotiationStrategy nextStrategy,
		NegotiationStrategyChangeTrigger trigger,
		String rationale
	) {
		static StrategyAdvice none() {
			return new StrategyAdvice(false, null, null, null);
		}
	}
}
