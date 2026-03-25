package org.GLM.negoriator.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.GLM.negoriator.domain.NegotiationSessionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:negotiator-controller-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
	"spring.datasource.driverClassName=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NegotiationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private NegotiationSessionRepository sessionRepository;

	@AfterEach
	void cleanUp() {
		sessionRepository.deleteAll();
	}

	@Test
	void startSessionHonorsCustomConfigurationAndIncludesOpeningBuyerMessage() throws Exception {
		String requestBody = """
			{
			  "strategy": "BOULWARE",
			  "maxRounds": 9,
			  "riskOfWalkaway": 0.35,
			  "buyerProfile": {
			    "idealOffer": {
			      "price": 91.00,
			      "paymentDays": 75,
			      "deliveryDays": 8,
			      "contractMonths": 4
			    },
			    "reservationOffer": {
			      "price": 125.00,
			      "paymentDays": 35,
			      "deliveryDays": 25,
			      "contractMonths": 20
			    },
			    "weights": {
			      "price": 0.45,
			      "paymentDays": 0.20,
			      "deliveryDays": 0.20,
			      "contractMonths": 0.15
			    },
			    "reservationUtility": 0.30
			  },
			  "bounds": {
			    "minPrice": 80.00,
			    "maxPrice": 130.00,
			    "minPaymentDays": 30,
			    "maxPaymentDays": 90,
			    "minDeliveryDays": 7,
			    "maxDeliveryDays": 30,
			    "minContractMonths": 3,
			    "maxContractMonths": 24
			  },
			  "supplierModel": {
			    "archetypeBeliefs": {
			      "MARGIN_FOCUSED": 0.40,
			      "CASHFLOW_FOCUSED": 0.20,
			      "OPERATIONS_FOCUSED": 0.25,
			      "STABILITY_FOCUSED": 0.15
			    },
			    "reservationUtility": 0.55
			  }
			}
			""";

		MvcResult createResult = mockMvc.perform(post("/api/negotiations/sessions")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.strategy").value("BOULWARE"))
			.andExpect(jsonPath("$.maxRounds").value(9))
			.andExpect(jsonPath("$.riskOfWalkaway").value(0.35))
			.andExpect(jsonPath("$.bounds.maxPrice").value(130.00))
			.andExpect(jsonPath("$.supplierModel.reservationUtility").value(0.55))
			.andReturn();

		JsonNode responseJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
		UUID sessionId = UUID.fromString(responseJson.get("id").asText());
		String sessionToken = responseJson.get("sessionToken").asText();

		assertFalse(sessionToken.isBlank());

		mockMvc.perform(get("/api/negotiations/sessions/{sessionId}", sessionId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(sessionId.toString()))
			.andExpect(jsonPath("$.sessionToken").value(sessionToken))
			.andExpect(jsonPath("$.strategyDetails[0].name").isNotEmpty())
			.andExpect(jsonPath("$.conversation[0].actor").value("system"))
			.andExpect(jsonPath("$.conversation[0].eventType").value("STRATEGY_CHANGE"))
			.andExpect(jsonPath("$.conversation[0].title").value("Session started"))
			.andExpect(jsonPath("$.conversation[1].actor").value("buyer"))
			.andExpect(jsonPath("$.conversation[1].eventType").value("BUYER_OPENING"))
			.andExpect(jsonPath("$.conversation[1].message").isNotEmpty());
	}

	@Test
	void updatesSessionSettingsAndReturnsUpdatedStrategyHistory() throws Exception {
		MvcResult createResult = mockMvc.perform(post("/api/negotiations/sessions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isCreated())
			.andReturn();

		UUID sessionId = UUID.fromString(
			objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText());

		String updateBody = """
			{
			  "strategy": "CONCEDER",
			  "maxRounds": 10,
			  "riskOfWalkaway": 0.22,
			  "buyerProfile": {
			    "idealOffer": {
			      "price": 89.00,
			      "paymentDays": 70,
			      "deliveryDays": 8,
			      "contractMonths": 6
			    },
			    "reservationOffer": {
			      "price": 120.00,
			      "paymentDays": 30,
			      "deliveryDays": 30,
			      "contractMonths": 24
			    },
			    "weights": {
			      "price": 0.45,
			      "paymentDays": 0.20,
			      "deliveryDays": 0.20,
			      "contractMonths": 0.15
			    },
			    "reservationUtility": 0.0
			  },
			  "bounds": {
			    "minPrice": 80.00,
			    "maxPrice": 125.00,
			    "minPaymentDays": 30,
			    "maxPaymentDays": 90,
			    "minDeliveryDays": 7,
			    "maxDeliveryDays": 30,
			    "minContractMonths": 3,
			    "maxContractMonths": 24
			  }
			}
			""";

		mockMvc.perform(put("/api/negotiations/sessions/{sessionId}/settings", sessionId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.strategy").value("CONCEDER"))
			.andExpect(jsonPath("$.strategyDetails[0].label").isNotEmpty())
			.andExpect(jsonPath("$.maxRounds").value(10))
			.andExpect(jsonPath("$.riskOfWalkaway").value(0.22))
			.andExpect(jsonPath("$.buyerProfile.idealOffer.price").value(89.00))
			.andExpect(jsonPath("$.bounds.maxPrice").value(125.00))
			.andExpect(jsonPath("$.strategyHistory[1].trigger").value("MANUAL_CONFIGURATION"))
			.andExpect(jsonPath("$.conversation[2].eventType").value("STRATEGY_CHANGE"))
			.andExpect(jsonPath("$.conversation[2].title").value("Strategy updated"))
			.andExpect(jsonPath("$.conversation[2].message").value("Session settings updated. Future rounds will use Conceder."))
			.andExpect(jsonPath("$.conversation[2].debug.switchTrigger").value("MANUAL_CONFIGURATION"));
	}

	@Test
	void updatesSessionSettingsWithoutStrategyChangeAndStillAddsConversationEvent() throws Exception {
		MvcResult createResult = mockMvc.perform(post("/api/negotiations/sessions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isCreated())
			.andReturn();

		UUID sessionId = UUID.fromString(
			objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText());

		String updateBody = """
			{
			  "strategy": "BASELINE",
			  "maxRounds": 10,
			  "riskOfWalkaway": 0.22,
			  "buyerProfile": {
			    "idealOffer": {
			      "price": 89.00,
			      "paymentDays": 70,
			      "deliveryDays": 8,
			      "contractMonths": 6
			    },
			    "reservationOffer": {
			      "price": 120.00,
			      "paymentDays": 30,
			      "deliveryDays": 30,
			      "contractMonths": 24
			    },
			    "weights": {
			      "price": 0.45,
			      "paymentDays": 0.20,
			      "deliveryDays": 0.20,
			      "contractMonths": 0.15
			    },
			    "reservationUtility": 0.0
			  },
			  "bounds": {
			    "minPrice": 80.00,
			    "maxPrice": 125.00,
			    "minPaymentDays": 30,
			    "maxPaymentDays": 90,
			    "minDeliveryDays": 7,
			    "maxDeliveryDays": 30,
			    "minContractMonths": 3,
			    "maxContractMonths": 24
			  }
			}
			""";

		mockMvc.perform(put("/api/negotiations/sessions/{sessionId}/settings", sessionId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.strategy").value("BASELINE"))
			.andExpect(jsonPath("$.strategyHistory[1].trigger").value("MANUAL_CONFIGURATION"))
			.andExpect(jsonPath("$.conversation[2].title").value("Settings updated"))
			.andExpect(jsonPath("$.conversation[2].message").value("Session settings updated."));
	}

	@Test
	void updatingSessionSettingsWithNoChangesDoesNotAddConversationEvent() throws Exception {
		MvcResult createResult = mockMvc.perform(post("/api/negotiations/sessions")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isCreated())
			.andReturn();

		String createResponse = createResult.getResponse().getContentAsString();
		JsonNode createdSession = objectMapper.readTree(createResponse);
		UUID sessionId = UUID.fromString(createdSession.get("id").asText());

		mockMvc.perform(put("/api/negotiations/sessions/{sessionId}/settings", sessionId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(java.util.Map.of(
					"strategy", createdSession.get("strategy").asText(),
					"maxRounds", createdSession.get("maxRounds").asInt(),
					"riskOfWalkaway", createdSession.get("riskOfWalkaway").decimalValue(),
					"buyerProfile", createdSession.get("buyerProfile"),
					"bounds", createdSession.get("bounds")))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.strategyHistory.length()").value(1))
			.andExpect(jsonPath("$.conversation.length()").value(2));
	}
}
