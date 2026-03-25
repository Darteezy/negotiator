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
	private static final String OPENING_FALLBACK = "Good day, please submit your initial commercial proposal, including price, payment terms, delivery schedule, and proposed contract term.";

	private final AiGatewayService aiGatewayService;

	public AiNegotiationMessageService(AiGatewayService aiGatewayService) {
		this.aiGatewayService = aiGatewayService;
	}

	public String composeOpeningMessage(NegotiationSession session) {
		StrategyMetadata.StrategyDescriptor descriptor = StrategyMetadata.describe(session.getStrategy());
		String systemPrompt = "You write buyer-side procurement messages to a supplier. "
			+ "Write like an experienced procurement manager sending a short formal business email note on behalf of the buyer organization. "
			+ "The message must sound like a real person in a live commercial discussion. "
			+ "Use natural business English, not negotiation theory or chatbot phrasing. "
			+ "Use a formal and official procurement tone, not casual wording. "
			+ "Write one short sentence. A brief formal greeting at the start is allowed if it feels natural. "
			+ "No sign-off, no markdown. "
			+ "Do not mention internal strategy, targets, scoring, algorithms, utilities, or hidden constraints. "
			+ "The opening should read like a formal request for the supplier's initial commercial terms. "
			+ descriptor.openingPromptGuidance() + " "
			+ "Ask the supplier for an opening offer covering price, payment days, delivery days, and contract length.";

		String userPrompt = "Round: " + session.getCurrentRound()
			+ " of " + session.getMaxRounds() + "\n"
			+ "Draft a short formal opening note from the buyer organization to the supplier.";

		return completeOrFallback(systemPrompt, userPrompt, OPENING_FALLBACK);
	}

	public String composeBuyerReply(BuyerReplyMessageRequest request) {
		StrategyMetadata.StrategyDescriptor descriptor = StrategyMetadata.describe(request.strategy());
		boolean allowMesoOptionList = request.strategy() == NegotiationStrategy.MESO
			&& request.counterOffers() != null
			&& request.counterOffers().size() > 1
			&& "COUNTER".equalsIgnoreCase(request.decision());
		String systemPrompt = "You write buyer-side procurement messages to a supplier in a live negotiation. "
			+ "Write like an experienced procurement manager sending a short business email note. "
			+ "The message must feel like it was written by a real person in an active commercial exchange. "
			+ "Use natural business English, not negotiation theory, consulting jargon, or chatbot phrasing. "
			+ (allowMesoOptionList
				? "Write a short intro followed by a simple bullet list with one concise option per bullet. "
				: "Write 2 to 4 short sentences in a single paragraph. ")
			+ "A brief greeting at the start is allowed when it feels natural. Prefer email tone over chat tone. "
			+ (allowMesoOptionList
				? "No sign-off. Markdown bullet points are allowed only for the option list. "
				: "No sign-off, no markdown, no bullet points. ")
			+ "Only mention commercial points the supplier can observe: the supplier's latest proposal, any concrete movement worth acknowledging, the buyer's position, and the next step. "
			+ "Do not reveal or hint at internal strategy names, targets, utilities, scoring, algorithms, reservation logic, hidden limits, or tactical reasoning. "
			+ "Do not use phrases like 'price gap', 'our target', 'workable range', 'commitment to a partnership', 'move for this to become workable', or similar analytical wording. "
			+ descriptor.replyPromptGuidance() + " "
			+ "If countering, keep the message constructive, state the buyer position in plain business language, and invite the supplier to respond. "
			+ (allowMesoOptionList
				? "For MESO multi-option counters, keep the intro short and present each option as its own bullet. "
				: "")
			+ "If accepting, clearly confirm the agreement. "
			+ "If rejecting, close respectfully without explaining internal thresholds.";

		String userPrompt = "Buyer decision: " + request.decision() + "\n"
			+ "Negotiation status after reply: " + request.resultingStatus() + "\n"
			+ "Round: " + request.roundNumber() + " of " + request.maxRounds() + "\n"
			+ "Supplier message: " + blankIfMissing(request.supplierMessage()) + "\n"
			+ "Supplier terms: " + formatTerms(request.supplierTerms()) + "\n"
			+ "Buyer terms to communicate: " + formatNullableTerms(request.primaryBuyerTerms()) + "\n"
			+ "Alternative buyer terms: " + formatCounterOffers(request.counterOffers()) + "\n"
			+ "Supplier-facing guidance: " + buildSupplierFacingGuidance(request, allowMesoOptionList) + "\n"
			+ "Write the exact buyer message to send to the supplier.";

		return completeOrFallback(systemPrompt, userPrompt, buildBuyerReplyFallback(request, allowMesoOptionList));
	}

	private String completeOrFallback(String systemPrompt, String userPrompt, String fallback) {
		try {
			String content = aiGatewayService.complete(systemPrompt, userPrompt);
			String normalized = normalize(content);
			return StringUtils.hasText(normalized) && !looksLikeMetaAssistantReply(normalized)
				? normalized
				: fallback;
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

	private boolean looksLikeMetaAssistantReply(String content) {
		String normalized = content.toLowerCase(Locale.ROOT);
		return normalized.contains("you haven't included the actual question")
			|| normalized.contains("what is the specific question")
			|| normalized.contains("for example, are you asking me to")
			|| normalized.contains("please provide the complete question")
			|| normalized.contains("i notice you've provided detailed context")
			|| normalized.contains("something else entirely?");
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

	private String buildSupplierFacingGuidance(
		BuyerReplyMessageRequest request,
		boolean allowMesoOptionList
	) {
		if ("ACCEPT".equalsIgnoreCase(request.decision())) {
			return "Confirm the agreement clearly and keep the tone concise and professional.";
		}

		if ("REJECT".equalsIgnoreCase(request.decision())) {
			return "Close respectfully, state that the current terms will not work, and do not explain internal limits.";
		}

		StringBuilder guidance = new StringBuilder("Keep the negotiation open and businesslike. ");

		if (allowMesoOptionList) {
			guidance.append("Present the buyer positions as different workable structures and ask which option is closest on the supplier side. ");
		} else if (request.primaryBuyerTerms() != null) {
			guidance.append("State the buyer terms plainly as the basis for continuing. ");
		}

		if (request.focusIssue() == null) {
			guidance.append("Keep the language concrete and commercial.");
			return guidance.toString().trim();
		}

		switch (request.focusIssue()) {
			case PRICE -> guidance.append("The main open point is price. Ask for a better price or state the buyer price plainly.");
			case PAYMENT_DAYS -> guidance.append("The main open point is payment terms. State that longer payment terms are still needed.");
			case DELIVERY_DAYS -> guidance.append("The main open point is delivery timing. State that faster delivery is still needed.");
			case CONTRACT_MONTHS -> guidance.append("The main open point is contract length. State that a shorter contract term is still needed.");
		}

		return guidance.toString().trim();
	}

	private String buildBuyerReplyFallback(BuyerReplyMessageRequest request, boolean allowMesoOptionList) {
		if ("ACCEPT".equalsIgnoreCase(request.decision())) {
			if (request.primaryBuyerTerms() == null) {
				return "Thank you. We can confirm the agreement.";
			}
			return "Thank you. We can confirm agreement on "
				+ formatTermsForMessage(request.primaryBuyerTerms()) + ".";
		}

		if ("REJECT".equalsIgnoreCase(request.decision())) {
			return "Thank you for the proposal. We won't be able to proceed on the current terms.";
		}

		if (allowMesoOptionList && request.counterOffers() != null && !request.counterOffers().isEmpty()) {
			StringBuilder sb = new StringBuilder("Thank you for the update. We can continue on one of the following structures:\n");
			for (int index = 0; index < request.counterOffers().size(); index++) {
				sb.append("- Option ").append(index + 1).append(": ")
					.append(formatTermsForMessage(request.counterOffers().get(index))).append("\n");
			}
			sb.append("Please let me know which option is closest on your side.");
			return sb.toString();
		}

		if (request.primaryBuyerTerms() == null) {
			return "Thank you for the update. Please send a revised offer if you would like to keep negotiating.";
		}

		return "Thank you for the update. To keep this moving, we would need "
			+ formatTermsForMessage(request.primaryBuyerTerms())
			+ ". Let me know if you can work on that basis.";
	}

	private String formatTermsForMessage(OfferVector terms) {
		return "price " + terms.price()
			+ ", payment in " + terms.paymentDays() + " days"
			+ ", delivery in " + terms.deliveryDays() + " days"
			+ ", and a " + terms.contractMonths() + " month contract";
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
		OfferEvaluation evaluation
	) {
	}
}
