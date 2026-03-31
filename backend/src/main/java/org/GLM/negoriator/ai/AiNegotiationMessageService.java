package org.GLM.negoriator.ai;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.GLM.negoriator.application.StrategyMetadata;
import org.GLM.negoriator.domain.NegotiationSession;
import org.GLM.negoriator.negotiation.NegotiationEngine.DecisionReason;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationIssue;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferEvaluation;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiNegotiationMessageService {

	private static final Logger log = LoggerFactory.getLogger(AiNegotiationMessageService.class);
	private static final String MESO_MULTI_OPTION_INTRO = "Thank you for the update. We can continue on one of the following structures:";
	private static final Resource OPENING_SYSTEM_TEMPLATE = new ClassPathResource("prompts/ai/opening-system.st");
	private static final Resource OPENING_USER_TEMPLATE = new ClassPathResource("prompts/ai/opening-user.st");
	private static final Resource BUYER_REPLY_SYSTEM_TEMPLATE = new ClassPathResource("prompts/ai/buyer-reply-system.st");
	private static final Resource BUYER_REPLY_USER_TEMPLATE = new ClassPathResource("prompts/ai/buyer-reply-user.st");

	private final AiGatewayService aiGatewayService;

	public AiNegotiationMessageService(AiGatewayService aiGatewayService) {
		this.aiGatewayService = aiGatewayService;
	}

	public String composeOpeningMessage(NegotiationSession session) {
		StrategyMetadata.StrategyDescriptor descriptor = StrategyMetadata.describe(session.getStrategy());
		String systemPrompt = renderTemplate(OPENING_SYSTEM_TEMPLATE, Map.of(
			"openingPromptGuidance", descriptor.openingPromptGuidance()));
		String userPrompt = renderTemplate(OPENING_USER_TEMPLATE, Map.of(
			"currentRound", session.getCurrentRound(),
			"maxRounds", session.getMaxRounds()));

		return completeOrFallback(systemPrompt, userPrompt, buildOpeningFallback(session.getStrategy()));
	}

	public String composeBuyerReply(BuyerReplyMessageRequest request) {
		boolean allowMesoOptionList = request.strategy() == NegotiationStrategy.MESO
			&& request.counterOffers() != null
			&& request.counterOffers().size() > 1
			&& "COUNTER".equalsIgnoreCase(request.decision());
		if (allowMesoOptionList) {
			return buildMesoBuyerReply(request.counterOffers());
		}

		StrategyMetadata.StrategyDescriptor descriptor = StrategyMetadata.describe(request.strategy());
		String systemPrompt = renderTemplate(BUYER_REPLY_SYSTEM_TEMPLATE, Map.of(
			"formatInstruction", allowMesoOptionList
				? "Write a short intro followed by a simple bullet list with one concise option per bullet."
				: "Write 2 to 4 short sentences in a single paragraph.",
			"markupInstruction", allowMesoOptionList
				? "No sign-off. Markdown bullet points are allowed only for the option list."
				: "No sign-off, no markdown, no bullet points.",
			"replyPromptGuidance", descriptor.replyPromptGuidance(),
			"mesoInstruction", allowMesoOptionList
				? "For MESO multi-option counters, keep the intro short and present each option as its own bullet."
				: ""));

		String userPrompt = renderTemplate(BUYER_REPLY_USER_TEMPLATE, Map.of(
			"decision", request.decision(),
			"resultingStatus", request.resultingStatus(),
			"roundNumber", request.roundNumber(),
			"maxRounds", request.maxRounds(),
			"supplierMessage", blankIfMissing(request.supplierMessage()),
			"supplierTerms", formatTerms(request.supplierTerms()),
			"primaryBuyerTerms", formatNullableTerms(request.primaryBuyerTerms()),
			"counterOffers", formatCounterOffers(request.counterOffers()),
			"supplierFacingGuidance", buildSupplierFacingGuidance(request, allowMesoOptionList)));

		return completeOrFallback(systemPrompt, userPrompt, buildBuyerReplyFallback(request, allowMesoOptionList));
	}

	private String buildMesoBuyerReply(List<OfferVector> counterOffers) {
		StringBuilder sb = new StringBuilder(MESO_MULTI_OPTION_INTRO).append("\n");
		for (int index = 0; index < counterOffers.size(); index++) {
			sb.append("- Option ").append(index + 1).append(": ")
				.append(formatTermsForMessage(counterOffers.get(index))).append("\n");
		}
		sb.append("Please let me know which option is closest on your side.");
		return sb.toString();
	}

	private String renderTemplate(Resource resource, Map<String, Object> params) {
		return new PromptTemplate(resource).render(params);
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

	private String buildOpeningFallback(NegotiationStrategy strategy) {
		return switch (strategy) {
			case BASELINE -> "Good day, please submit your initial commercial proposal, including price, payment terms, delivery schedule, and proposed contract term.";
			case MESO -> "Good day, please submit your initial commercial proposal, including price, payment terms, delivery schedule, and proposed contract term. If there are alternative workable structures on your side, feel free to outline them.";
			case BOULWARE -> "Good day, please submit your best initial commercial proposal, including price, payment terms, delivery schedule, and proposed contract term.";
			case CONCEDER -> "Good day, please send your opening commercial proposal, including price, payment terms, delivery schedule, and proposed contract term, so we can work toward a practical agreement quickly.";
			case TIT_FOR_TAT -> "Good day, please submit your initial commercial proposal, including price, payment terms, delivery schedule, and proposed contract term. We will respond directly to the movement shown in your offer.";
		};
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
			return buildMesoBuyerReply(request.counterOffers());
		}

		if (request.primaryBuyerTerms() == null) {
			return "Thank you for the update. Please send a revised offer if you would like to keep negotiating.";
		}

		return switch (request.strategy()) {
			case BASELINE -> "Thank you for the update. To keep this moving, we would need "
				+ formatTermsForMessage(request.primaryBuyerTerms())
				+ ". Let me know if you can work on that basis.";
			case MESO -> "Thank you for the update. One workable structure on our side would be "
				+ formatTermsForMessage(request.primaryBuyerTerms())
				+ ". If that direction is close, let me know what still needs adjustment.";
			case BOULWARE -> "Thank you for the proposal. We can continue if you can move to "
				+ formatTermsForMessage(request.primaryBuyerTerms())
				+ ". If that is achievable, send your confirmation on that basis.";
			case CONCEDER -> "Thank you for the movement. To keep momentum and move this toward agreement, we could proceed on "
				+ formatTermsForMessage(request.primaryBuyerTerms())
				+ ". Let me know if you can close on that basis.";
			case TIT_FOR_TAT -> "Thank you for the update. In response, we could continue on "
				+ formatTermsForMessage(request.primaryBuyerTerms())
				+ ". Let me know whether you can match that direction on your side.";
		};
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
