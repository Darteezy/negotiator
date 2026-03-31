package org.GLM.negoriator.application;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.GLM.negoriator.ai.AiGatewayService;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
class SupplierMessageIntentAiFallbackService {

	private static final Resource INTENT_PROMPT_TEMPLATE = new ClassPathResource("prompts/ai/intent-fallback-system.st");
	private static final Logger log = LoggerFactory.getLogger(SupplierMessageIntentAiFallbackService.class);

	private final AiGatewayService aiGatewayService;
	private final BeanOutputConverter<AiIntentFallbackResponse> outputConverter;
	private final ObjectMapper objectMapper;

	SupplierMessageIntentAiFallbackService(AiGatewayService aiGatewayService, ObjectMapper objectMapper) {
		this.aiGatewayService = aiGatewayService;
		this.outputConverter = new BeanOutputConverter<>(AiIntentFallbackResponse.class, objectMapper);
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
			AiIntentFallbackResponse aiResponse = aiGatewayService.completeStructured(
				renderPrompt(INTENT_PROMPT_TEMPLATE),
				requestJson,
				outputConverter);
			SupplierMessageIntentParser.SupplierIntentType type = readIntentType(aiResponse.intentType());
			Integer selectedCounterOfferIndex = aiResponse.selectedCounterOfferIndex();

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
				containsAcceptanceSignal,
				SupplierMessageIntentParser.SupplierIntentSource.AI_FALLBACK));
		} catch (Exception exception) {
			log.warn("AI fallback intent resolution failed: {}", exception.getMessage());
			log.debug("AI fallback intent resolution stack trace", exception);
			return Optional.empty();
		}
	}

	private String renderPrompt(Resource resource) {
		return new PromptTemplate(resource).render();
	}

	private SupplierMessageIntentParser.SupplierIntentType readIntentType(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}

		try {
			return SupplierMessageIntentParser.SupplierIntentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private record AiIntentFallbackRequest(
		String supplierMessage,
		OfferVector supplierTerms,
		List<OfferVector> activeBuyerOffers
	) {
	}

	private record AiIntentFallbackResponse(
		String intentType,
		Integer selectedCounterOfferIndex
	) {
	}
}
