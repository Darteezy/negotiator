package org.GLM.negoriator.ai;

import java.util.List;
import java.util.Locale;

import org.GLM.negoriator.application.StrategyMetadata;
import org.GLM.negoriator.domain.NegotiationSession;
import org.GLM.negoriator.negotiation.NegotiationEngine.DecisionReason;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationIssue;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferEvaluation;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiNegotiationMessageService {

	private static final Logger log = LoggerFactory.getLogger(AiNegotiationMessageService.class);
	private static final String OPENING_FALLBACK = "Hello, please share your opening offer including price, payment days, delivery days, and contract length.";

	private final AiGatewayService aiGatewayService;

	public AiNegotiationMessageService(AiGatewayService aiGatewayService) {
		this.aiGatewayService = aiGatewayService;
	}

	public String composeOpeningMessage(NegotiationSession session) {
		StrategyMetadata.StrategyDescriptor descriptor = StrategyMetadata.describe(session.getStrategy());
		String systemPrompt = "You write procurement negotiation chat messages for the buyer. "
			+ "Use a professional, polite email/chat hybrid tone. "
			+ "Write one short sentence. A brief greeting at the start is allowed. "
			+ "No sign-off, no markdown. "
			+ descriptor.openingPromptGuidance() + " "
			+ "Ask the supplier for an opening offer covering price, payment days, delivery days, and contract length.";

		String userPrompt = "Current buyer strategy: " + descriptor.label() + " (" + session.getStrategy().name() + ")"
			+ ". Negotiation round: " + session.getCurrentRound()
			+ " of " + session.getMaxRounds()
			+ ". Write the buyer's opening request.";

		return completeOrFallback(systemPrompt, userPrompt, OPENING_FALLBACK);
	}

	public String composeBuyerReply(BuyerReplyMessageRequest request) {
		StrategyMetadata.StrategyDescriptor descriptor = StrategyMetadata.describe(request.strategy());
		boolean allowMesoOptionList = request.strategy() == NegotiationStrategy.MESO
			&& request.counterOffers() != null
			&& request.counterOffers().size() > 1
			&& "COUNTER".equalsIgnoreCase(request.decision());
		String systemPrompt = "You write buyer-side negotiation messages for a live procurement discussion. "
			+ "Use a professional, polite email/chat hybrid tone. "
			+ (allowMesoOptionList
				? "Write a short intro followed by a dotted list with one concise option per bullet. "
				: "Write 2 to 4 short sentences in a single paragraph. ")
			+ "A brief greeting at the start is allowed when it feels natural. "
			+ (allowMesoOptionList
				? "No sign-off. Markdown bullet points are allowed only for the option list. "
				: "No sign-off, no markdown, no bullet points. ")
			+ "Do not mention internal strategy names, utilities, reason codes, algorithms, or reservation logic. "
			+ descriptor.replyPromptGuidance() + " "
			+ "If the supplier offer is close but still outside the buyer range, keep the message constructive and steer the supplier back inside workable terms. "
			+ "If countering, clearly state the buyer position and invite the supplier to respond. "
			+ (allowMesoOptionList
				? "For MESO multi-option counters, keep the intro short and present each option as its own bullet. "
				: "")
			+ "If accepting, clearly confirm the agreement. "
			+ "If rejecting, close respectfully and state that the offer is outside the buyer's workable range.";

		String userPrompt = "Buyer decision: " + request.decision() + "\n"
			+ "Negotiation status after reply: " + request.resultingStatus() + "\n"
			+ "Round: " + request.roundNumber() + " of " + request.maxRounds() + "\n"
			+ "Internal buyer strategy context: " + descriptor.label() + " (" + request.strategy().name() + ")\n"
			+ "Internal strategy rationale: " + blankIfMissing(request.strategyRationale()) + "\n"
			+ "Strategy boundary posture: " + descriptor.boundaryStyle() + "\n"
			+ "Fallback message intent: " + request.fallbackMessage() + "\n"
			+ "Supplier message: " + blankIfMissing(request.supplierMessage()) + "\n"
			+ "Supplier terms: " + formatTerms(request.supplierTerms()) + "\n"
			+ "Primary buyer terms: " + formatNullableTerms(request.primaryBuyerTerms()) + "\n"
			+ "Alternative buyer terms: " + formatCounterOffers(request.counterOffers()) + "\n"
			+ "Reason code: " + blankIfMissing(request.reasonCode() == null ? null : request.reasonCode().name()) + "\n"
			+ "Focus issue: " + blankIfMissing(request.focusIssue() == null ? null : request.focusIssue().name()) + "\n"
			+ "Evaluation summary: " + formatEvaluation(request.evaluation()) + "\n"
			+ "Write the exact buyer message to send to the supplier.";

		return completeOrFallback(systemPrompt, userPrompt, request.fallbackMessage());
	}

	private String completeOrFallback(String systemPrompt, String userPrompt, String fallback) {
		try {
			String content = aiGatewayService.complete(systemPrompt, userPrompt);
			String normalized = normalize(content);
			return StringUtils.hasText(normalized) ? normalized : fallback;
		} catch (Exception exception) {
			log.debug("AI negotiation message generation unavailable, using fallback text.", exception);
			return fallback;
		}
	}

	private String normalize(String content) {
		if (!StringUtils.hasText(content)) {
			return null;
		}

		String normalized = content.trim();
		if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() > 1) {
			normalized = normalized.substring(1, normalized.length() - 1).trim();
		}

		String[] lines = normalized.split("\\R", -1);
		StringBuilder sb = new StringBuilder();
		for (String line : lines) {
			String cleanedLine = line.replaceAll("[\\t ]+", " ").trim();
			if (cleanedLine.isEmpty()) {
				continue;
			}
			if (!sb.isEmpty()) {
				sb.append('\n');
			}
			sb.append(cleanedLine);
		}

		return sb.toString();
	}

	private String formatTerms(OfferVector terms) {
		return "price " + terms.price()
			+ ", payment " + terms.paymentDays() + " days"
			+ ", delivery " + terms.deliveryDays() + " days"
			+ ", contract " + terms.contractMonths() + " months";
	}

	private String formatNullableTerms(OfferVector terms) {
		return terms == null ? "none" : formatTerms(terms);
	}

	private String formatCounterOffers(List<OfferVector> counterOffers) {
		if (counterOffers == null || counterOffers.isEmpty()) {
			return "none";
		}

		StringBuilder sb = new StringBuilder();
		for (int index = 0; index < counterOffers.size(); index++) {
			if (index > 0) {
				sb.append(" | ");
			}
			sb.append("option ").append(index + 1).append(": ")
				.append(formatTerms(counterOffers.get(index)));
		}
		return sb.toString();
	}

	private String formatEvaluation(OfferEvaluation evaluation) {
		if (evaluation == null) {
			return "none";
		}

		return String.format(
			Locale.ROOT,
			"buyerUtility=%s, estimatedSupplierUtility=%s, targetUtility=%s, continuationValue=%s, nashProduct=%s",
			evaluation.buyerUtility(),
			evaluation.estimatedSupplierUtility(),
			evaluation.targetUtility(),
			evaluation.continuationValue(),
			evaluation.nashProduct());
	}

	private String blankIfMissing(String value) {
		return StringUtils.hasText(value) ? value : "none";
	}

	public record BuyerReplyMessageRequest(
		int roundNumber,
		int maxRounds,
		String decision,
		String resultingStatus,
		String supplierMessage,
		OfferVector supplierTerms,
		OfferVector primaryBuyerTerms,
		List<OfferVector> counterOffers,
		DecisionReason reasonCode,
		NegotiationIssue focusIssue,
		NegotiationStrategy strategy,
		String strategyRationale,
		OfferEvaluation evaluation,
		String fallbackMessage
	) {
	}
}