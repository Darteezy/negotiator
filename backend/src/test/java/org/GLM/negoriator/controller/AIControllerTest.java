package org.GLM.negoriator.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.GLM.negoriator.ai.AiGatewayService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class AIControllerTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void parseOfferReturnsServiceUnavailableWhenAiModelIsUnavailable() {
		AiGatewayService failingGateway = new AiGatewayService(
			RestClient.builder(),
			objectMapper,
			"ollama",
			"http://localhost:11434",
			"test-model",
			""
		) {
			@Override
			public String complete(String systemPrompt, String userPrompt) {
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

	private record Terms(
		double price,
		int paymentDays,
		int deliveryDays,
		int contractMonths
	) {
	}
}