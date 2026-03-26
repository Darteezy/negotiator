package org.GLM.negoriator.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.GLM.negoriator.ai.AiGatewayService;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class SupplierMessageIntentAiFallbackServiceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void returnsResolvedIntentWhenAiProvidesStructuredAnswer() {
		SupplierMessageIntentAiFallbackService service = new SupplierMessageIntentAiFallbackService(
			new StubAiGatewayService("{\"intentType\":\"ACCEPT_ACTIVE_OFFER\",\"selectedCounterOfferIndex\":2}"),
			objectMapper);

		SupplierMessageIntentParser.SupplierMessageIntent intent = service.resolve(
			"That package works on our side.",
			new OfferVector(new BigDecimal("100.00"), 50, 20, 10),
			List.of(
				new OfferVector(new BigDecimal("102.00"), 57, 10, 12),
				new OfferVector(new BigDecimal("104.00"), 55, 8, 9)))
			.orElseThrow();

		assertEquals(SupplierMessageIntentParser.SupplierIntentType.ACCEPT_ACTIVE_OFFER, intent.type());
		assertEquals(SupplierMessageIntentParser.SupplierIntentSource.AI_FALLBACK, intent.source());
		assertEquals(2, intent.selectedBuyerOfferIndex());
		assertTrue(intent.referencesBuyerOffer());
	}

	@Test
	void returnsEmptyWhenAiResponseIsInvalid() {
		SupplierMessageIntentAiFallbackService service = new SupplierMessageIntentAiFallbackService(
			new StubAiGatewayService("not-json"),
			objectMapper);

		assertTrue(service.resolve(
			"That package works on our side.",
			new OfferVector(new BigDecimal("100.00"), 50, 20, 10),
			List.of(new OfferVector(new BigDecimal("102.00"), 57, 10, 12)))
			.isEmpty());
	}

	@Test
	void returnsEmptyWhenAiResolvesOutOfRangeOption() {
		SupplierMessageIntentAiFallbackService service = new SupplierMessageIntentAiFallbackService(
			new StubAiGatewayService("{\"intentType\":\"SELECT_COUNTER_OPTION\",\"selectedCounterOfferIndex\":3}"),
			objectMapper);

		assertFalse(service.resolve(
			"That package works on our side.",
			new OfferVector(new BigDecimal("100.00"), 50, 20, 10),
			List.of(new OfferVector(new BigDecimal("102.00"), 57, 10, 12)))
			.isPresent());
	}

	private final class StubAiGatewayService extends AiGatewayService {

		private final String response;

		private StubAiGatewayService(String response) {
			super(
				RestClient.builder().requestFactory(new JdkClientHttpRequestFactory()),
				objectMapper,
				"ollama",
				"http://localhost:11434",
				"test-model",
				"");
			this.response = response;
		}

		@Override
		public String completeJson(String systemPrompt, String userPrompt) {
			return response;
		}
	}
}
