package org.GLM.negoriator.ai;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	private static final Pattern STRATEGY_PATTERN = Pattern.compile(
		"\"strategy\"\\s*:\\s*\"(MESO|BOULWARE|CONCEDER|TIT_FOR_TAT|BASELINE)\"",
		Pattern.CASE_INSENSITIVE);
	private static final Pattern RATIONALE_PATTERN = Pattern.compile(
		"\"rationale\"\\s*:\\s*\"([^\"]+)\"");

	private final AiGatewayService aiGateway;

	public AiStrategyAdvisor(AiGatewayService aiGateway) {
		this.aiGateway = aiGateway;
	}

	public StrategyAdvice advise(NegotiationSession session, OfferEvaluation evaluation) {
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
				.thenComparing(NegotiationOffer::getCreatedAt))
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
		Matcher strategyMatcher = STRATEGY_PATTERN.matcher(response);
		if (!strategyMatcher.find()) {
			return StrategyAdvice.none();
		}

		String recommended = strategyMatcher.group(1).toUpperCase();
		if ("KEEP".equals(recommended)) {
			return StrategyAdvice.none();
		}

		NegotiationStrategy nextStrategy;
		try {
			nextStrategy = NegotiationStrategy.valueOf(recommended);
		} catch (IllegalArgumentException e) {
			return StrategyAdvice.none();
		}

		if (nextStrategy == currentStrategy) {
			return StrategyAdvice.none();
		}

		String rationale = "AI recommended switching to " + nextStrategy.name();
		Matcher rationaleMatcher = RATIONALE_PATTERN.matcher(response);
		if (rationaleMatcher.find()) {
			rationale = rationaleMatcher.group(1);
		}

		return new StrategyAdvice(true, nextStrategy, NegotiationStrategyChangeTrigger.AI_RECOMMENDATION, rationale);
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
