package org.GLM.negoriator.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.GLM.negoriator.ai.AiGatewayService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class AIControllerTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void parseOfferUsesAiSelectedCounterOfferAsSemanticBase() {
		AiGatewayService gateway = new StubAiGatewayService("""
			{"selectedCounterOfferIndex":2,"price":null,"paymentDays":null,"deliveryDays":12,"contractMonths":null}
			""");

		AIController controller = new AIController(gateway, objectMapper);

		AIController.ParseOfferResponse response = controller.parseOffer(new AIController.ParseOfferRequest(
			"The second option works for us if delivery is 12 days.",
			new Terms(110.0, 40, 29, 20),
			java.util.List.of(
				new Terms(100.0, 40, 29, 20),
				new Terms(110.0, 40, 18, 20),
				new Terms(110.0, 50, 29, 20)
			)
		));

		assertEquals(110.0, response.price());
		assertEquals(40, response.paymentDays());
		assertEquals(12, response.deliveryDays());
		assertEquals(20, response.contractMonths());
		assertEquals(null, response.supplierConstraints());
	}

	@Test
	void parseOfferUsesBuyerTermsAgreementWhenAiSignalsAgreement() {
		AiGatewayService gateway = new StubAiGatewayService("""
			{"selectedCounterOfferIndex":null,"agreesToBuyerTerms":true,"price":null,"paymentDays":null,"deliveryDays":null,"contractMonths":null}
			""");

		AIController controller = new AIController(gateway, objectMapper);

		AIController.ParseOfferResponse response = controller.parseOffer(new AIController.ParseOfferRequest(
			"Your proposal works for us. We can proceed.",
			new Terms(120.0, 30, 14, 12),
			java.util.List.of(new Terms(119.0, 45, 12, 12))
		));

		assertEquals(119.0, response.price());
		assertEquals(45, response.paymentDays());
		assertEquals(12, response.deliveryDays());
		assertEquals(12, response.contractMonths());
		assertEquals(null, response.supplierConstraints());
	}

	@Test
	void parseOfferReturnsHardSupplierConstraintsWhenAiSignalsThem() {
		AiGatewayService gateway = new StubAiGatewayService("""
			{"price":110,"paymentDays":40,"deliveryDays":29,"contractMonths":20,"selectedCounterOfferIndex":null,"agreesToBuyerTerms":false,"supplierConstraints":{"priceFloor":110,"paymentDaysCeiling":null,"deliveryDaysFloor":null,"contractMonthsFloor":null}}
			""");

		AIController controller = new AIController(gateway, objectMapper);

		AIController.ParseOfferResponse response = controller.parseOffer(new AIController.ParseOfferRequest(
			"We cannot lower the price. 110 is only possible lowest price.",
			new Terms(110.0, 40, 29, 20),
			java.util.List.of()
		));

		assertEquals(110.0, response.price());
		assertEquals(110.0, response.supplierConstraints().priceFloor());
	}

	@Test
	void parseOfferIgnoresAiConstraintsWhenMessageOnlyStatesCurrentTerms() {
		AiGatewayService gateway = new StubAiGatewayService("""
			{"price":120,"paymentDays":50,"deliveryDays":30,"contractMonths":12,"selectedCounterOfferIndex":null,"agreesToBuyerTerms":false,"supplierConstraints":{"priceFloor":120,"paymentDaysCeiling":50,"deliveryDaysFloor":30,"contractMonthsFloor":12}}
			""");

		AIController controller = new AIController(gateway, objectMapper);

		AIController.ParseOfferResponse response = controller.parseOffer(new AIController.ParseOfferRequest(
			"Hi Price 120 delivery 30, payment 50, contract 12",
			new Terms(90.0, 60, 7, 6),
			java.util.List.of()
		));

		assertEquals(120.0, response.price());
		assertEquals(50, response.paymentDays());
		assertEquals(30, response.deliveryDays());
		assertEquals(12, response.contractMonths());
		assertEquals(null, response.supplierConstraints());
	}

	@Test
	void parseOfferKeepsExplicitConstraintPhrasesAcrossAllDimensions() {
		AiGatewayService gateway = new StubAiGatewayService("""
			{"price":120,"paymentDays":50,"deliveryDays":30,"contractMonths":12,"selectedCounterOfferIndex":null,"agreesToBuyerTerms":false,"supplierConstraints":{"priceFloor":120,"paymentDaysCeiling":50,"deliveryDaysFloor":30,"contractMonthsFloor":12}}
			""");

		AIController controller = new AIController(gateway, objectMapper);

		AIController.ParseOfferResponse response = controller.parseOffer(new AIController.ParseOfferRequest(
			"The lowest price is 120, maximum payment is 50 days, the fastest delivery we can do is 30 days, and the minimum contract is 12 months.",
			new Terms(90.0, 60, 7, 6),
			java.util.List.of()
		));

		assertEquals(120.0, response.supplierConstraints().priceFloor());
		assertEquals(50, response.supplierConstraints().paymentDaysCeiling());
		assertEquals(30, response.supplierConstraints().deliveryDaysFloor());
		assertEquals(12, response.supplierConstraints().contractMonthsFloor());
	}

	@Test
	void parseOfferAppliesSupplierConstraintsToSelectedCounterOfferBase() {
		AiGatewayService gateway = new StubAiGatewayService("""
			{"selectedCounterOfferIndex":3,"price":null,"paymentDays":null,"deliveryDays":null,"contractMonths":null,"supplierConstraints":{"priceFloor":null,"paymentDaysCeiling":null,"deliveryDaysFloor":12,"contractMonthsFloor":null}}
			""");

		AIController controller = new AIController(gateway, objectMapper);

		AIController.ParseOfferResponse response = controller.parseOffer(new AIController.ParseOfferRequest(
			"We want to proceed with option 3, but keep delivery at least 12 days.",
			new Terms(120.0, 30, 14, 12),
			java.util.List.of(
				new Terms(112.0, 30, 20, 12),
				new Terms(108.0, 45, 14, 10),
				new Terms(100.0, 50, 10, 8)
			)
		));

		assertEquals(100.0, response.price());
		assertEquals(50, response.paymentDays());
		assertEquals(12, response.deliveryDays());
		assertEquals(8, response.contractMonths());
		assertEquals(12, response.supplierConstraints().deliveryDaysFloor());
	}

	@Test
	void parseOfferReturnsServiceUnavailableWhenAiModelIsUnavailable() {
		AiGatewayService failingGateway = new AiGatewayService("ollama", "test-model", null) {
			@Override
			public <T> T completeStructured(
				String systemPrompt,
				String userPrompt,
				org.springframework.ai.converter.StructuredOutputConverter<T> outputConverter
			) {
				throw new IllegalArgumentException("AI provider returned an empty response.");
			}
		};

		AIController controller = new AIController(failingGateway, objectMapper);

		ResponseStatusException exception = assertThrows(
			ResponseStatusException.class,
			() -> controller.parseOffer(new AIController.ParseOfferRequest(
				"We can do option 2",
				new Terms(120.0, 30, 14, 12),
				java.util.List.of(
					new Terms(119.0, 45, 12, 12),
					new Terms(120.0, 60, 10, 10)
				)
			)));

		assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
		assertEquals(
			"Supplier message parsing requires a configured and reachable AI model.",
			exception.getReason());
	}

	private final class StubAiGatewayService extends AiGatewayService {

		private final String response;

		private StubAiGatewayService(String response) {
			super("ollama", "test-model", null);
			this.response = response;
		}

		@Override
		public <T> T completeStructured(
			String systemPrompt,
			String userPrompt,
			org.springframework.ai.converter.StructuredOutputConverter<T> outputConverter
		) {
			return outputConverter.convert(response);
		}
	}

	private record Terms(
		double price,
		int paymentDays,
		int deliveryDays,
		int contractMonths
	) {
	}
}
