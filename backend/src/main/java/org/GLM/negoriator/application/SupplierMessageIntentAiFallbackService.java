package org.GLM.negoriator.application;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.GLM.negoriator.ai.AiGatewayService;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
class SupplierMessageIntentAiFallbackService {

	private static final String INTENT_PROMPT = "You classify a supplier message in a commercial negotiation. "
		+ "Return JSON only with keys intentType and selectedCounterOfferIndex. "
		+ "Valid intentType values: ACCEPT_ACTIVE_OFFER, SELECT_COUNTER_OPTION, PROPOSE_NEW_TERMS, REJECT_OR_DECLINE, UNCLEAR. "
		+ "Infer from meaning, not exact keywords. Use activeBuyerOffers as the current buyer options, where activeBuyerOffers[0] is option 1. "
		+ "Only return ACCEPT_ACTIVE_OFFER or SELECT_COUNTER_OPTION when the supplier is clearly referring to one of the active buyer offers. "
		+ "If the supplier is choosing or approving a described offer like the faster delivery option or the longer payment option, map it to the correct selectedCounterOfferIndex. "
		+ "If there is not enough confidence, return UNCLEAR and selectedCounterOfferIndex null. "
		+ "Do not include markdown fences or extra commentary.";

	private final AiGatewayService aiGatewayService;
	private final ObjectMapper objectMapper;

	SupplierMessageIntentAiFallbackService(AiGatewayService aiGatewayService, ObjectMapper objectMapper) {
		this.aiGatewayService = aiGatewayService;
		this.objectMapper = objectMapper;
	}

	Optional<SupplierMessageIntentParser.SupplierMessageIntent> resolve(
		String supplierMessage,
		OfferVector supplierTerms,
		List<OfferVector> activeBuyerOffers
	) {
		if (!StringUtils.hasText(supplierMessage) || activeBuyerOffers == null || activeBuyerOffers.isEmpty()) {
			return Optional.empty();
		}

		try {
			String requestJson = objectMapper.writeValueAsString(new AiIntentFallbackRequest(
				supplierMessage,
				supplierTerms,
				activeBuyerOffers));
			String content = aiGatewayService.completeJson(INTENT_PROMPT, requestJson);
			JsonNode json = extractJson(content);
			SupplierMessageIntentParser.SupplierIntentType type = readIntentType(json);
			Integer selectedCounterOfferIndex = readInteger(json, "selectedCounterOfferIndex");

			if (type == null || type == SupplierMessageIntentParser.SupplierIntentType.UNCLEAR) {
				return Optional.empty();
			}

			if (selectedCounterOfferIndex != null
				&& (selectedCounterOfferIndex < 1 || selectedCounterOfferIndex > activeBuyerOffers.size())) {
				return Optional.empty();
			}

			boolean referencesBuyerOffer = selectedCounterOfferIndex != null
				|| type == SupplierMessageIntentParser.SupplierIntentType.ACCEPT_ACTIVE_OFFER
				|| type == SupplierMessageIntentParser.SupplierIntentType.SELECT_COUNTER_OPTION;
			boolean containsAcceptanceSignal = type == SupplierMessageIntentParser.SupplierIntentType.ACCEPT_ACTIVE_OFFER;

			if ((type == SupplierMessageIntentParser.SupplierIntentType.ACCEPT_ACTIVE_OFFER
				|| type == SupplierMessageIntentParser.SupplierIntentType.SELECT_COUNTER_OPTION)
				&& !referencesBuyerOffer) {
				return Optional.empty();
			}

			return Optional.of(new SupplierMessageIntentParser.SupplierMessageIntent(
				type,
				selectedCounterOfferIndex,
				referencesBuyerOffer,
				containsAcceptanceSignal));
		} catch (Exception exception) {
			return Optional.empty();
		}
	}

	private JsonNode extractJson(String content) throws Exception {
		if (!StringUtils.hasText(content)) {
			throw new IllegalArgumentException("AI parser returned no content.");
		}

		int start = content.indexOf('{');
		int end = content.lastIndexOf('}');
		if (start < 0 || end <= start) {
			throw new IllegalArgumentException("AI parser did not return JSON.");
		}

		return objectMapper.readTree(content.substring(start, end + 1));
	}

	private SupplierMessageIntentParser.SupplierIntentType readIntentType(JsonNode json) {
		JsonNode value = json.get("intentType");
		if (value == null || value.isNull() || !value.isTextual()) {
			return null;
		}

		try {
			return SupplierMessageIntentParser.SupplierIntentType.valueOf(value.asText().trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private Integer readInteger(JsonNode json, String fieldName) {
		JsonNode value = json.get(fieldName);
		return value == null || value.isNull() ? null : value.asInt();
	}

	private record AiIntentFallbackRequest(
		String supplierMessage,
		OfferVector supplierTerms,
		List<OfferVector> activeBuyerOffers
	) {
	}
}
