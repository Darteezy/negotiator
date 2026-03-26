package org.GLM.negoriator.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.GLM.negoriator.domain.BuyerProfileSnapshot;
import org.GLM.negoriator.domain.IssueWeightsSnapshot;
import org.GLM.negoriator.domain.NegotiationBoundsSnapshot;
import org.GLM.negoriator.domain.NegotiationSession;
import org.GLM.negoriator.domain.NegotiationSessionStatus;
import org.GLM.negoriator.domain.OfferTermsSnapshot;
import org.GLM.negoriator.domain.SupplierBeliefSnapshot;
import org.GLM.negoriator.domain.SupplierModelSnapshot;
import org.GLM.negoriator.negotiation.NegotiationEngine.DecisionReason;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationIssue;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferEvaluation;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AiNegotiationMessageServiceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void composeOpeningMessageBuildsFormalOfficialPrompt() {
		CapturingAiGatewayService gateway = new CapturingAiGatewayService(
			"Good day, please submit your initial commercial proposal, including price, payment terms, delivery schedule, and proposed contract term."
		);
		AiNegotiationMessageService service = new AiNegotiationMessageService(gateway);

		String response = service.composeOpeningMessage(buildSession());

		assertEquals(
			"Good day, please submit your initial commercial proposal, including price, payment terms, delivery schedule, and proposed contract term.",
			response);
		assertTrue(gateway.systemPrompt.contains("formal business email note on behalf of the buyer organization"));
		assertTrue(gateway.systemPrompt.contains("formal and official procurement tone"));
		assertTrue(gateway.systemPrompt.contains("formal request for the supplier's initial commercial terms"));
		assertTrue(gateway.userPrompt.contains("formal opening note from the buyer organization"));
	}

	@Test
	void composeBuyerReplyBuildsHumanSupplierFacingPrompt() {
		CapturingAiGatewayService gateway = new CapturingAiGatewayService(
			"Thanks for the update. Moving delivery to 15 days helps. We still need a better price to proceed."
		);
		AiNegotiationMessageService service = new AiNegotiationMessageService(gateway);

		String response = service.composeBuyerReply(new AiNegotiationMessageService.BuyerReplyMessageRequest(
			2,
			6,
			"COUNTER",
			"COUNTERED",
			"We can do 108 if delivery moves to 15 days.",
			new OfferVector(new BigDecimal("108.00"), 30, 15, 12),
			new OfferVector(new BigDecimal("102.00"), 45, 15, 12),
			List.of(new OfferVector(new BigDecimal("102.00"), 45, 15, 12)),
			DecisionReason.COUNTER_TO_CLOSE_GAP,
			NegotiationIssue.PRICE,
			NegotiationStrategy.BASELINE,
			"Baseline is active as the balanced default strategy with a steady concession pace.",
			new OfferEvaluation(
				new BigDecimal("0.6200"),
				new BigDecimal("0.4100"),
				new BigDecimal("0.7800"),
				new BigDecimal("0.3500"),
				new BigDecimal("0.1200"))
		));

		assertEquals(
			"Thanks for the update. Moving delivery to 15 days helps. We still need a better price to proceed.",
			response);
		assertTrue(gateway.systemPrompt.contains("experienced procurement manager"));
		assertTrue(gateway.systemPrompt.contains("real person"));
		assertTrue(gateway.systemPrompt.contains("Do not reveal or hint at internal strategy names"));
		assertTrue(gateway.systemPrompt.contains("Do not use phrases like 'price gap'"));
		assertFalse(gateway.userPrompt.contains("Internal buyer strategy context"));
		assertFalse(gateway.userPrompt.contains("Fallback message intent"));
		assertFalse(gateway.userPrompt.contains("Evaluation summary"));
		assertFalse(gateway.userPrompt.contains("Reason code"));
		assertFalse(gateway.userPrompt.contains("Focus issue"));
		assertTrue(gateway.userPrompt.contains("Supplier-facing guidance:"));
		assertTrue(gateway.userPrompt.contains("The main open point is price."));
	}

	@Test
	void composeBuyerReplyUsesHumanFallbackWhenAiReturnsBlank() {
		CapturingAiGatewayService gateway = new CapturingAiGatewayService("   ");
		AiNegotiationMessageService service = new AiNegotiationMessageService(gateway);

		String response = service.composeBuyerReply(new AiNegotiationMessageService.BuyerReplyMessageRequest(
			3,
			6,
			"COUNTER",
			"COUNTERED",
			"108 is our best price.",
			new OfferVector(new BigDecimal("108.00"), 30, 15, 12),
			new OfferVector(new BigDecimal("102.00"), 45, 14, 12),
			List.of(new OfferVector(new BigDecimal("102.00"), 45, 14, 12)),
			DecisionReason.COUNTER_TO_CLOSE_GAP,
			NegotiationIssue.PRICE,
			NegotiationStrategy.BASELINE,
			"Baseline is active as the balanced default strategy with a steady concession pace.",
			new OfferEvaluation(
				new BigDecimal("0.6200"),
				new BigDecimal("0.4100"),
				new BigDecimal("0.7800"),
				new BigDecimal("0.3500"),
				new BigDecimal("0.1200"))
		));

		assertEquals(
			"Thank you for the update. To keep this moving, we would need price 102.00, payment in 45 days, delivery in 14 days, and a 12 month contract. Let me know if you can work on that basis.",
			response);
		assertFalse(response.contains("buyer utility"));
		assertFalse(response.contains("reservation"));
		assertFalse(response.contains("target"));
	}

	@Test
	void composeBuyerReplyUsesFallbackWhenAiReturnsMetaAssistantResponse() {
		CapturingAiGatewayService gateway = new CapturingAiGatewayService(
			"I notice you've provided detailed context for a procurement negotiation scenario, but you haven't included the actual question or problem that needs solving."
		);
		AiNegotiationMessageService service = new AiNegotiationMessageService(gateway);

		String response = service.composeBuyerReply(new AiNegotiationMessageService.BuyerReplyMessageRequest(
			2,
			6,
			"COUNTER",
			"COUNTERED",
			"We agree with option 1",
			new OfferVector(new BigDecimal("100.00"), 50, 14, 10),
			new OfferVector(new BigDecimal("95.00"), 50, 14, 10),
			List.of(new OfferVector(new BigDecimal("95.00"), 50, 14, 10)),
			DecisionReason.COUNTER_TO_CLOSE_GAP,
			NegotiationIssue.PRICE,
			NegotiationStrategy.MESO,
			"Meso is active with multiple structured options.",
			new OfferEvaluation(
				new BigDecimal("0.7000"),
				new BigDecimal("0.4200"),
				new BigDecimal("0.7600"),
				new BigDecimal("0.3500"),
				new BigDecimal("0.1100"))
		));

		assertEquals(
			"Thank you for the update. To keep this moving, we would need price 95.00, payment in 50 days, delivery in 14 days, and a 10 month contract. Let me know if you can work on that basis.",
			response);
	}

	private final class CapturingAiGatewayService extends AiGatewayService {

		private final String response;
		private String systemPrompt;
		private String userPrompt;

		private CapturingAiGatewayService(String response) {
			super(
				RestClient.builder(),
				objectMapper,
				"ollama",
				"http://localhost:11434",
				"test-model",
				""
			);
			this.response = response;
		}

		@Override
		public String complete(String systemPrompt, String userPrompt) {
			this.systemPrompt = systemPrompt;
			this.userPrompt = userPrompt;
			return response;
		}
	}

	private NegotiationSession buildSession() {
		return new NegotiationSession(
			1,
			6,
			NegotiationStrategy.BASELINE,
			new BigDecimal("0.1500"),
			NegotiationSessionStatus.PENDING,
			new BuyerProfileSnapshot(
				new OfferTermsSnapshot(new BigDecimal("90.00"), 60, 7, 6),
				new OfferTermsSnapshot(new BigDecimal("120.00"), 30, 30, 24),
				new IssueWeightsSnapshot(
					new BigDecimal("0.4000"),
					new BigDecimal("0.2000"),
					new BigDecimal("0.2000"),
					new BigDecimal("0.2000")),
				new BigDecimal("0.6000")),
			new NegotiationBoundsSnapshot(
				new BigDecimal("80.00"),
				new BigDecimal("130.00"),
				15,
				60,
				5,
				35,
				3,
				36),
			new SupplierModelSnapshot(
				new SupplierBeliefSnapshot(
					new BigDecimal("0.2500"),
					new BigDecimal("0.2500"),
					new BigDecimal("0.2500"),
					new BigDecimal("0.2500")),
				new BigDecimal("0.4000")));
	}
}
