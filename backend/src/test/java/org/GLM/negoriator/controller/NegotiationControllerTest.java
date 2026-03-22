package org.GLM.negoriator.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.GLM.negoriator.application.NegotiationApplicationService;
import org.GLM.negoriator.application.NegotiationDefaults;
import org.GLM.negoriator.domain.NegotiationSession;
import org.GLM.negoriator.domain.NegotiationSessionRepository;
import org.GLM.negoriator.domain.NegotiationSessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NegotiationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private NegotiationApplicationService negotiationApplicationService;

	@Autowired
	private NegotiationSessionRepository negotiationSessionRepository;

	@Test
	void returnsConfiguredDefaults() throws Exception {
		mockMvc.perform(get("/api/negotiations/config/defaults"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.defaultStrategy").value("MESO"))
			.andExpect(jsonPath("$.availableStrategies").isArray())
			.andExpect(jsonPath("$.maxRounds").value(NegotiationDefaults.maxRounds()))
			.andExpect(jsonPath("$.riskOfWalkaway").value(0.15))
			.andExpect(jsonPath("$.bounds.minPrice").value(80.00))
			.andExpect(jsonPath("$.bounds.maxPrice").value(120.00))
			.andExpect(jsonPath("$.bounds.minPaymentDays").value(30))
			.andExpect(jsonPath("$.bounds.maxContractMonths").value(24));
	}

	@Test
	void startsSessionWithDefaultConfigurationWhenBodyIsOmitted() throws Exception {
		mockMvc.perform(post("/api/negotiations/sessions"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.strategy").value("MESO"))
			.andExpect(jsonPath("$.strategyHistory[0].trigger").value("INITIAL_SELECTION"))
			.andExpect(jsonPath("$.conversation[0].eventType").value("STRATEGY_CHANGE"))
			.andExpect(jsonPath("$.currentRound").value(1))
			.andExpect(jsonPath("$.maxRounds").value(NegotiationDefaults.maxRounds()))
			.andExpect(jsonPath("$.status").value(NegotiationSessionStatus.PENDING.name()))
			.andExpect(jsonPath("$.closed").value(false))
			.andExpect(jsonPath("$.rounds").isArray())
			.andExpect(jsonPath("$.rounds").isEmpty())
			.andExpect(jsonPath("$.bounds.minDeliveryDays").value(3));
	}

	@Test
	void returnsExistingSessionById() throws Exception {
		NegotiationSession session = negotiationApplicationService.startSession(NegotiationDefaults.startSessionCommand());

		mockMvc.perform(get("/api/negotiations/sessions/{sessionId}", session.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(session.getId().toString()))
			.andExpect(jsonPath("$.strategy").value("MESO"))
			.andExpect(jsonPath("$.strategyHistory[0].nextStrategy").value("MESO"))
			.andExpect(jsonPath("$.status").value(NegotiationSessionStatus.PENDING.name()))
			.andExpect(jsonPath("$.rounds").isArray())
			.andExpect(jsonPath("$.rounds").isEmpty());
	}

	@Test
	void submitsSupplierOfferAndReturnsPersistedNegotiationRound() throws Exception {
		UUID sessionId = startSessionAndExtractId();

		mockMvc.perform(post("/api/negotiations/sessions/{sessionId}/offers", sessionId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "price": 104.00,
					  "paymentDays": 45,
					  "deliveryDays": 10,
					  "contractMonths": 12
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(sessionId.toString()))
			.andExpect(jsonPath("$.strategy").value("MESO"))
			.andExpect(jsonPath("$.strategyHistory.length()").value(1))
			.andExpect(jsonPath("$.status").value(NegotiationSessionStatus.COUNTERED.name()))
			.andExpect(jsonPath("$.currentRound").value(2))
			.andExpect(jsonPath("$.rounds[0].roundNumber").value(1))
			.andExpect(jsonPath("$.rounds[0].supplierOffer.terms.price").value(104.00))
			.andExpect(jsonPath("$.rounds[0].buyerReply.decision").value("COUNTER"))
			.andExpect(jsonPath("$.rounds[0].buyerReply.resultingStatus").value(NegotiationSessionStatus.COUNTERED.name()))
			.andExpect(jsonPath("$.rounds[0].buyerReply.reasonCode").value("COUNTER_TO_CLOSE_GAP"))
			.andExpect(jsonPath("$.rounds[0].buyerReply.focusIssue").isNotEmpty())
			.andExpect(jsonPath("$.rounds[0].buyerReply.strategyUsed").value("MESO"))
			.andExpect(jsonPath("$.rounds[0].buyerReply.strategyRationale").isNotEmpty())
			.andExpect(jsonPath("$.rounds[0].buyerReply.counterOffers").isArray())
			.andExpect(jsonPath("$.rounds[0].buyerReply.counterOffers.length()").value(org.hamcrest.Matchers.greaterThan(1)))
			.andExpect(jsonPath("$.conversation[*].eventType", org.hamcrest.Matchers.hasItems("SUPPLIER_OFFER", "BUYER_REPLY", "STRATEGY_CHANGE")))
			.andExpect(jsonPath("$.conversation[*].debug.strategy", org.hamcrest.Matchers.hasItem("MESO")))
			.andExpect(jsonPath("$.rounds[0].buyerReply.counterOffer.price").exists())
			.andExpect(jsonPath("$.rounds[0].buyerReply.evaluation.buyerUtility").exists());
	}

	@Test
	void returnsBadRequestWhenSupplierOfferPayloadIsIncomplete() throws Exception {
		UUID sessionId = startSessionAndExtractId();

		mockMvc.perform(post("/api/negotiations/sessions/{sessionId}/offers", sessionId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("price is required."));
	}

	@Test
	void returnsNotFoundForMissingSession() throws Exception {
		UUID missingSessionId = UUID.randomUUID();

		mockMvc.perform(get("/api/negotiations/sessions/{sessionId}", missingSessionId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value("Negotiation session not found: " + missingSessionId));
	}

	@Test
	void returnsConflictWhenSubmittingOfferToClosedSession() throws Exception {
		NegotiationSession session = negotiationApplicationService.startSession(NegotiationDefaults.startSessionCommand());
		session.moveTo(NegotiationSessionStatus.ACCEPTED);
		negotiationSessionRepository.saveAndFlush(session);

		mockMvc.perform(post("/api/negotiations/sessions/{sessionId}/offers", session.getId())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "price": 104.00,
					  "paymentDays": 45,
					  "deliveryDays": 10,
					  "contractMonths": 12
					}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value("Negotiation session is closed: " + session.getId()));
	}

	private UUID startSessionAndExtractId() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/negotiations/sessions"))
			.andExpect(status().isCreated())
			.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		return UUID.fromString(body.get("id").asText());
	}
}