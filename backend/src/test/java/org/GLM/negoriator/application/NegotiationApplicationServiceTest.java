package org.GLM.negoriator.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Comparator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.GLM.negoriator.domain.NegotiationDecision;
import org.GLM.negoriator.domain.NegotiationDecisionType;
import org.GLM.negoriator.domain.NegotiationOffer;
import org.GLM.negoriator.domain.NegotiationSession;
import org.GLM.negoriator.domain.NegotiationSessionRepository;
import org.GLM.negoriator.domain.NegotiationSessionStatus;
import org.GLM.negoriator.domain.NegotiationStrategyChangeTrigger;
import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.IssueWeights;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.GLM.negoriator.ai.AiGatewayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@SpringBootTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:negotiator-service-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
	"spring.datasource.driverClassName=org.h2.Driver",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"spring.jpa.open-in-view=false"
})
@ActiveProfiles("test")
class NegotiationApplicationServiceTest {

	@Autowired
	private NegotiationApplicationService service;

	@Autowired
	private NegotiationSessionRepository sessionRepository;

	@Autowired
	private ControlledAiGatewayService aiGatewayService;

	@BeforeEach
	void resetAiGateway() {
		aiGatewayService.reset();
	}

	@AfterEach
	void cleanUp() {
		sessionRepository.deleteAll();
	}

	@Test
	void keepsAcceptableSupplierCounterOpenUntilSupplierExplicitlyAccepts() {
		NegotiationSession startedSession = service.startSession(NegotiationDefaults.startSessionCommand());

		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 30, 14, 24), "Price 120, payment days 30, delivery 14 days, contract length 24 months");
		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 30, 14, 18), "Increase price to 120 and contract term 18 months");
		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 30, 14, 12), "Increase price to 120 and contract term 12 months");
		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 30, 7, 6), "Increase price to 120 and contract term decrease to 6 months and delivery in 7 days");

		NegotiationSession pendingAcceptanceSession = submit(
			startedSession,
			new OfferVector(new BigDecimal("120.00"), 60, 7, 12),
			"Increase price to 120 and increase payment days to 60 days");
		NegotiationDecision pendingAcceptanceDecision = latestDecision(pendingAcceptanceSession);

		assertEquals(NegotiationSessionStatus.COUNTERED, pendingAcceptanceSession.getStatus());
		assertEquals(NegotiationDecisionType.COUNTER, pendingAcceptanceDecision.getDecision());
		assertNotNull(pendingAcceptanceDecision.getCounterOffer());
		assertNotNull(pendingAcceptanceDecision.getExplanation());
		assertTrue(!pendingAcceptanceDecision.getExplanation().isBlank());
		assertTrue(pendingAcceptanceDecision.getCounterOffer().toOfferVector().price().compareTo(new BigDecimal("120.00")) <= 0);
		assertEquals(60, pendingAcceptanceDecision.getCounterOffer().toOfferVector().paymentDays());
		assertEquals(7, pendingAcceptanceDecision.getCounterOffer().toOfferVector().deliveryDays());
		assertEquals(12, pendingAcceptanceDecision.getCounterOffer().toOfferVector().contractMonths());

		NegotiationSession acceptedSession = submit(
			startedSession,
			new OfferVector(new BigDecimal("120.00"), 60, 7, 12),
			"accept");
		NegotiationDecision acceptedDecision = latestDecision(acceptedSession);

		assertEquals(NegotiationSessionStatus.ACCEPTED, acceptedSession.getStatus());
		assertEquals(NegotiationDecisionType.ACCEPT, acceptedDecision.getDecision());
		assertNotNull(acceptedDecision.getExplanation());
		assertTrue(!acceptedDecision.getExplanation().isBlank());
	}

	@Test
	void startSessionProvidesImmediateDefaultOpeningMessage() {
		NegotiationSession startedSession = service.startSession(NegotiationDefaults.startSessionCommand());

		assertNotNull(startedSession.getOpeningMessage());
		assertEquals(
			"Good day, please submit your initial commercial proposal, including price, payment terms, delivery schedule, and proposed contract term.",
			startedSession.getOpeningMessage());
	}

	@Test
	void updatesSessionSettingsForFutureRoundsAndRecordsManualStrategyChange() {
		NegotiationSession startedSession = service.startSession(NegotiationDefaults.startSessionCommand());

		NegotiationSession updatedSession = service.updateSessionSettings(
			startedSession.getId(),
			new NegotiationApplicationService.UpdateSessionSettingsCommand(
				NegotiationStrategy.BOULWARE,
				10,
				new BigDecimal("0.25"),
				new BuyerProfile(
					new OfferVector(new BigDecimal("88.00"), 70, 9, 6),
					new OfferVector(new BigDecimal("126.00"), 35, 28, 24),
					new IssueWeights(
						new BigDecimal("0.50"),
						new BigDecimal("0.20"),
						new BigDecimal("0.20"),
						new BigDecimal("0.10")),
					BigDecimal.ZERO),
				new NegotiationBounds(
					new BigDecimal("80.00"),
					new BigDecimal("130.00"),
					30,
					90,
					7,
					30,
					3,
					24)));

		NegotiationSession reloadedSession = service.getSession(updatedSession.getId());

		assertEquals(NegotiationStrategy.BOULWARE, reloadedSession.getStrategy());
		assertEquals(10, reloadedSession.getMaxRounds());
		assertEquals(0, new BigDecimal("0.25").compareTo(reloadedSession.getRiskOfWalkaway()));
		assertEquals(0, new BigDecimal("88.00").compareTo(reloadedSession.toBuyerProfile().idealOffer().price()));
		assertEquals(0, new BigDecimal("130.00").compareTo(reloadedSession.toNegotiationBounds().maxPrice()));
		assertTrue(reloadedSession.getStrategyChanges().stream()
			.anyMatch(change -> change.getTrigger() == NegotiationStrategyChangeTrigger.MANUAL_CONFIGURATION
				&& "Session settings updated. Future rounds will use Boulware.".equals(change.getRationale())));
	}

	@Test
	void recordsManualConfigurationChangeEvenWhenStrategyDoesNotChange() {
		NegotiationSession startedSession = service.startSession(NegotiationDefaults.startSessionCommand());

		NegotiationSession updatedSession = service.updateSessionSettings(
			startedSession.getId(),
			new NegotiationApplicationService.UpdateSessionSettingsCommand(
				NegotiationStrategy.BASELINE,
				9,
				new BigDecimal("0.20"),
				new BuyerProfile(
					new OfferVector(new BigDecimal("95.00"), 65, 11, 8),
					new OfferVector(new BigDecimal("126.00"), 35, 28, 24),
					new IssueWeights(
						new BigDecimal("0.50"),
						new BigDecimal("0.20"),
						new BigDecimal("0.20"),
						new BigDecimal("0.10")),
					BigDecimal.ZERO),
				new NegotiationBounds(
					new BigDecimal("80.00"),
					new BigDecimal("130.00"),
					30,
					90,
					7,
					30,
					3,
					24)));

		NegotiationSession reloadedSession = service.getSession(updatedSession.getId());

		assertTrue(reloadedSession.getStrategyChanges().stream()
			.anyMatch(change -> change.getTrigger() == NegotiationStrategyChangeTrigger.MANUAL_CONFIGURATION
				&& change.getPreviousStrategy() == NegotiationStrategy.BASELINE
				&& change.getNextStrategy() == NegotiationStrategy.BASELINE
				&& "Session settings updated.".equals(change.getRationale())));
	}

	@Test
	void doesNotRecordManualConfigurationChangeWhenNothingChanged() {
		NegotiationSession startedSession = service.startSession(NegotiationDefaults.startSessionCommand());

		NegotiationSession updatedSession = service.updateSessionSettings(
			startedSession.getId(),
			new NegotiationApplicationService.UpdateSessionSettingsCommand(
				startedSession.getStrategy(),
				startedSession.getMaxRounds(),
				startedSession.getRiskOfWalkaway(),
				startedSession.toBuyerProfile(),
				startedSession.toNegotiationBounds()));

		NegotiationSession reloadedSession = service.getSession(updatedSession.getId());

		assertEquals(1, reloadedSession.getStrategyChanges().size());
	}

	@Test
	void mesoOptionSelectionKeepsNegotiationOpenWithoutExplicitAcceptKeyword() {
		NegotiationSession startedSession = service.startSession(
			NegotiationDefaults.startSessionCommand(NegotiationStrategy.MESO));

		NegotiationSession counteredSession = submit(
			startedSession,
			new OfferVector(new BigDecimal("110.00"), 40, 29, 20),
			"Price 110, payment 40, delivery 29, contract 20");

		OfferVector selectedBuyerOption = counteredSession.getOffers().stream()
			.filter(offer -> offer.getParty() == org.GLM.negoriator.domain.NegotiationParty.BUYER)
			.filter(offer -> offer.getRoundNumber() == 1)
			.sorted(Comparator.comparing(org.GLM.negoriator.domain.NegotiationOffer::getCreatedAt))
			.skip(1)
			.findFirst()
			.orElseThrow()
			.toOfferVector();

		NegotiationSession selectedSession = submit(
			counteredSession,
			selectedBuyerOption,
			"option 2");
		NegotiationDecision selectedDecision = latestDecision(selectedSession);

		assertEquals(NegotiationSessionStatus.COUNTERED, selectedSession.getStatus());
		assertEquals(NegotiationDecisionType.COUNTER, selectedDecision.getDecision());
		assertTrue(selectedDecision.getSupplierOffer().toOfferVector().matches(selectedBuyerOption));
		assertNotNull(selectedDecision.getCounterOffer());
	}

	@Test
	void mesoOptionSelectionWithExplicitAcceptKeywordClosesDeal() {
		NegotiationSession startedSession = service.startSession(
			NegotiationDefaults.startSessionCommand(NegotiationStrategy.MESO));

		NegotiationSession counteredSession = submit(
			startedSession,
			new OfferVector(new BigDecimal("110.00"), 40, 29, 20),
			"Price 110, payment 40, delivery 29, contract 20");

		OfferVector selectedBuyerOption = counteredSession.getOffers().stream()
			.filter(offer -> offer.getParty() == org.GLM.negoriator.domain.NegotiationParty.BUYER)
			.filter(offer -> offer.getRoundNumber() == 1)
			.sorted(Comparator.comparing(org.GLM.negoriator.domain.NegotiationOffer::getCreatedAt))
			.skip(1)
			.findFirst()
			.orElseThrow()
			.toOfferVector();

		NegotiationSession acceptedSession = submit(
			counteredSession,
			selectedBuyerOption,
			"accept option 2");
		NegotiationDecision acceptedDecision = latestDecision(acceptedSession);

		assertEquals(NegotiationSessionStatus.ACCEPTED, acceptedSession.getStatus());
		assertEquals(NegotiationDecisionType.ACCEPT, acceptedDecision.getDecision());
		assertNotNull(acceptedDecision.getCounterOffer());
		assertTrue(acceptedDecision.getCounterOffer().toOfferVector().matches(selectedBuyerOption));
	}

	@Test
	void mesoOptionSelectionWithWeAgreeMessageClosesDeal() {
		NegotiationSession startedSession = service.startSession(
			NegotiationDefaults.startSessionCommand(NegotiationStrategy.MESO));

		NegotiationSession counteredSession = submit(
			startedSession,
			new OfferVector(new BigDecimal("100.00"), 50, 20, 10),
			"Price 100, payment 50, delivery 20, contract 10");

		OfferVector selectedBuyerOption = counteredSession.getOffers().stream()
			.filter(offer -> offer.getParty() == org.GLM.negoriator.domain.NegotiationParty.BUYER)
			.filter(offer -> offer.getRoundNumber() == 1)
			.sorted(Comparator.comparing(org.GLM.negoriator.domain.NegotiationOffer::getCreatedAt))
			.findFirst()
			.orElseThrow()
			.toOfferVector();

		NegotiationSession acceptedSession = submit(
			counteredSession,
			selectedBuyerOption,
			"We agree with option 1");
		NegotiationDecision acceptedDecision = latestDecision(acceptedSession);

		assertEquals(NegotiationSessionStatus.ACCEPTED, acceptedSession.getStatus());
		assertEquals(NegotiationDecisionType.ACCEPT, acceptedDecision.getDecision());
		assertNotNull(acceptedDecision.getCounterOffer());
		assertTrue(acceptedDecision.getCounterOffer().toOfferVector().matches(selectedBuyerOption));
	}

	@Test
	void mesoActionBasedOptionAcceptanceClosesDeal() {
		NegotiationSession startedSession = service.startSession(
			NegotiationDefaults.startSessionCommand(NegotiationStrategy.MESO));

		NegotiationSession counteredSession = submit(
			startedSession,
			new OfferVector(new BigDecimal("100.00"), 50, 20, 10),
			"Price 100, payment 50, delivery 20, contract 10");

		OfferVector selectedBuyerOption = counteredSession.getOffers().stream()
			.filter(offer -> offer.getParty() == org.GLM.negoriator.domain.NegotiationParty.BUYER)
			.filter(offer -> offer.getRoundNumber() == 1)
			.sorted(Comparator.comparing(org.GLM.negoriator.domain.NegotiationOffer::getCreatedAt))
			.findFirst()
			.orElseThrow()
			.toOfferVector();

		NegotiationSession acceptedSession = submit(
			counteredSession,
			selectedBuyerOption,
			"Let's go with the first package.");
		NegotiationDecision acceptedDecision = latestDecision(acceptedSession);

		assertEquals(NegotiationSessionStatus.ACCEPTED, acceptedSession.getStatus());
		assertEquals(NegotiationDecisionType.ACCEPT, acceptedDecision.getDecision());
		assertNotNull(acceptedDecision.getCounterOffer());
		assertTrue(acceptedDecision.getCounterOffer().toOfferVector().matches(selectedBuyerOption));
	}

	@Test
	void mesoDescriptiveOptionSelectionKeepsNegotiationOpen() {
		NegotiationSession startedSession = service.startSession(
			NegotiationDefaults.startSessionCommand(NegotiationStrategy.MESO));

		NegotiationSession counteredSession = submit(
			startedSession,
			new OfferVector(new BigDecimal("100.00"), 50, 20, 10),
			"Price 100, payment 50, delivery 20, contract 10");

		OfferVector fasterDeliveryOption = counteredSession.getOffers().stream()
			.filter(offer -> offer.getParty() == org.GLM.negoriator.domain.NegotiationParty.BUYER)
			.filter(offer -> offer.getRoundNumber() == 1)
			.min(Comparator.comparingInt(offer -> offer.toOfferVector().deliveryDays()))
			.orElseThrow()
			.toOfferVector();

		NegotiationSession selectedSession = submit(
			counteredSession,
			fasterDeliveryOption,
			"The faster delivery option is closest for us.");
		NegotiationDecision selectedDecision = latestDecision(selectedSession);

		assertEquals(NegotiationSessionStatus.COUNTERED, selectedSession.getStatus());
		assertEquals(NegotiationDecisionType.COUNTER, selectedDecision.getDecision());
		assertNotNull(selectedDecision.getCounterOffer());
	}

	@Test
	void mesoDescriptiveOptionAcceptanceClosesDeal() {
		NegotiationSession startedSession = service.startSession(
			NegotiationDefaults.startSessionCommand(NegotiationStrategy.MESO));

		NegotiationSession counteredSession = submit(
			startedSession,
			new OfferVector(new BigDecimal("100.00"), 50, 20, 10),
			"Price 100, payment 50, delivery 20, contract 10");

		OfferVector fasterDeliveryOption = counteredSession.getOffers().stream()
			.filter(offer -> offer.getParty() == org.GLM.negoriator.domain.NegotiationParty.BUYER)
			.filter(offer -> offer.getRoundNumber() == 1)
			.min(Comparator.comparingInt(offer -> offer.toOfferVector().deliveryDays()))
			.orElseThrow()
			.toOfferVector();

		NegotiationSession acceptedSession = submit(
			counteredSession,
			fasterDeliveryOption,
			"Let's go with the faster delivery option.");
		NegotiationDecision acceptedDecision = latestDecision(acceptedSession);

		assertEquals(NegotiationSessionStatus.ACCEPTED, acceptedSession.getStatus());
		assertEquals(NegotiationDecisionType.ACCEPT, acceptedDecision.getDecision());
		assertNotNull(acceptedDecision.getCounterOffer());
		assertTrue(acceptedDecision.getCounterOffer().toOfferVector().matches(fasterDeliveryOption));
	}

	@Test
	void transcriptStyleMesoConversationClosesImmediatelyOnOptionAgreement() {
		NegotiationSession startedSession = service.startSession(
			NegotiationDefaults.startSessionCommand(NegotiationStrategy.MESO));

		NegotiationSession counteredSession = submit(
			startedSession,
			new OfferVector(new BigDecimal("100.00"), 50, 20, 10),
			"Price 100, payment 50 days, delivery 20 days, contract 10 months");

		NegotiationOffer optionOne = counteredSession.getOffers().stream()
			.filter(offer -> offer.getParty() == org.GLM.negoriator.domain.NegotiationParty.BUYER)
			.filter(offer -> offer.getRoundNumber() == 1)
			.sorted(Comparator.comparing(org.GLM.negoriator.domain.NegotiationOffer::getCreatedAt))
			.findFirst()
			.orElseThrow();

		NegotiationSession acceptedSession = submit(
			counteredSession,
			optionOne.toOfferVector(),
			"We agree with option 1");
		NegotiationDecision acceptedDecision = latestDecision(acceptedSession);

		assertEquals(NegotiationSessionStatus.ACCEPTED, acceptedSession.getStatus());
		assertEquals(NegotiationDecisionType.ACCEPT, acceptedDecision.getDecision());
		assertNotNull(acceptedDecision.getCounterOffer());
		assertTrue(acceptedDecision.getCounterOffer().toOfferVector().matches(optionOne.toOfferVector()));
	}

	@Test
	void explicitAcceptCanCloseOnPriorBuyerOfferWithoutRepeatingBuyerTerms() {
		NegotiationSession startedSession = service.startSession(
			NegotiationDefaults.startSessionCommand(NegotiationStrategy.BOULWARE));

		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 60, 30, 24), "Price 120, payment 60, delivery 30, contract 24");
		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 60, 21, 24), "Price 120, payment 60, delivery 21, contract 24");
		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 60, 14, 24), "Price 120, payment 60, delivery 14, contract 24");
		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 60, 14, 12), "Price 120, payment 60, delivery 14, contract 12");
		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 60, 7, 12), "Price 120, payment 60, delivery 7, contract 12");
		NegotiationSession pendingAcceptanceSession = submit(
			startedSession,
			new OfferVector(new BigDecimal("120.00"), 60, 7, 12),
			"Price 120, payment 60, delivery 7, contract 12");

		NegotiationOffer lastBuyerOffer = pendingAcceptanceSession.getOffers().stream()
			.filter(offer -> offer.getParty() == org.GLM.negoriator.domain.NegotiationParty.BUYER)
			.filter(offer -> offer.getRoundNumber() == 6)
			.max(Comparator.comparing(org.GLM.negoriator.domain.NegotiationOffer::getCreatedAt))
			.orElseThrow();
		assertTrue(lastBuyerOffer.toOfferVector().price().compareTo(new BigDecimal("120.00")) < 0);

		NegotiationSession acceptedSession = submit(
			pendingAcceptanceSession,
			new OfferVector(new BigDecimal("120.00"), 60, 7, 12),
			"accept");
		NegotiationDecision acceptedDecision = latestDecision(acceptedSession);

		assertEquals(NegotiationSessionStatus.ACCEPTED, acceptedSession.getStatus());
		assertEquals(NegotiationDecisionType.ACCEPT, acceptedDecision.getDecision());
		assertNotNull(acceptedDecision.getCounterOffer());
		assertEquals(0, acceptedDecision.getCounterOffer().toOfferVector().price().compareTo(lastBuyerOffer.toOfferVector().price()));
		assertEquals(60, acceptedDecision.getCounterOffer().toOfferVector().paymentDays());
		assertEquals(7, acceptedDecision.getCounterOffer().toOfferVector().deliveryDays());
		assertEquals(12, acceptedDecision.getCounterOffer().toOfferVector().contractMonths());
	}

	@Test
	void explicitAcceptCanCloseOnPrimaryMesoOfferWithoutRepeatingBuyerTerms() {
		NegotiationSession startedSession = service.startSession(
			NegotiationDefaults.startSessionCommand(NegotiationStrategy.MESO));

		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 60, 30, 24), "Price 120, payment 60, delivery 30, contract 24");
		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 60, 21, 24), "Price 120, payment 60, delivery 21, contract 24");
		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 60, 14, 24), "Price 120, payment 60, delivery 14, contract 24");
		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 60, 14, 12), "Price 120, payment 60, delivery 14, contract 12");
		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 60, 7, 12), "Price 120, payment 60, delivery 7, contract 12");
		NegotiationSession pendingAcceptanceSession = submit(
			startedSession,
			new OfferVector(new BigDecimal("120.00"), 60, 7, 12),
			"Price 120, payment 60, delivery 7, contract 12");

		NegotiationOffer primaryBuyerOffer = pendingAcceptanceSession.getOffers().stream()
			.filter(offer -> offer.getParty() == org.GLM.negoriator.domain.NegotiationParty.BUYER)
			.filter(offer -> offer.getRoundNumber() == 6)
			.sorted(Comparator.comparing(org.GLM.negoriator.domain.NegotiationOffer::getCreatedAt))
			.findFirst()
			.orElseThrow();
		assertEquals(0, primaryBuyerOffer.toOfferVector().price().compareTo(new BigDecimal("113.40")));
		assertEquals(12, primaryBuyerOffer.toOfferVector().contractMonths());

		NegotiationSession acceptedSession = submit(
			pendingAcceptanceSession,
			new OfferVector(new BigDecimal("120.00"), 60, 7, 12),
			"accept");
		NegotiationDecision acceptedDecision = latestDecision(acceptedSession);

		assertEquals(NegotiationSessionStatus.ACCEPTED, acceptedSession.getStatus());
		assertEquals(NegotiationDecisionType.ACCEPT, acceptedDecision.getDecision());
		assertNotNull(acceptedDecision.getCounterOffer());
		assertTrue(acceptedDecision.getCounterOffer().toOfferVector().matches(primaryBuyerOffer.toOfferVector()));
	}

	@Test
	void exactBuyerOfferMatchCanCloseEvenWhenSupplierMessageIsUnclear() {
		NegotiationSession startedSession = service.startSession(
			NegotiationDefaults.startSessionCommand(NegotiationStrategy.BOULWARE));

		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 60, 30, 24), "Price 120, payment 60, delivery 30, contract 24");
		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 60, 21, 24), "Price 120, payment 60, delivery 21, contract 24");
		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 60, 14, 24), "Price 120, payment 60, delivery 14, contract 24");
		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 60, 14, 12), "Price 120, payment 60, delivery 14, contract 12");
		submit(startedSession, new OfferVector(new BigDecimal("120.00"), 60, 7, 12), "Price 120, payment 60, delivery 7, contract 12");
		NegotiationSession pendingAcceptanceSession = submit(
			startedSession,
			new OfferVector(new BigDecimal("120.00"), 60, 7, 12),
			"Price 120, payment 60, delivery 7, contract 12");

		NegotiationOffer lastBuyerOffer = pendingAcceptanceSession.getOffers().stream()
			.filter(offer -> offer.getParty() == org.GLM.negoriator.domain.NegotiationParty.BUYER)
			.filter(offer -> offer.getRoundNumber() == 6)
			.max(Comparator.comparing(org.GLM.negoriator.domain.NegotiationOffer::getCreatedAt))
			.orElseThrow();

		NegotiationSession acceptedSession = submit(
			pendingAcceptanceSession,
			lastBuyerOffer.toOfferVector(),
			"Understood.");
		NegotiationDecision acceptedDecision = latestDecision(acceptedSession);

		assertEquals(NegotiationSessionStatus.ACCEPTED, acceptedSession.getStatus());
		assertEquals(NegotiationDecisionType.ACCEPT, acceptedDecision.getDecision());
		assertNotNull(acceptedDecision.getCounterOffer());
		assertTrue(acceptedDecision.getCounterOffer().toOfferVector().matches(lastBuyerOffer.toOfferVector()));
	}

	@Test
	void unclearReplyTriggersClarificationInsteadOfFreshAlgorithmCounter() {
		NegotiationSession startedSession = service.startSession(
			NegotiationDefaults.startSessionCommand(NegotiationStrategy.MESO));

		NegotiationSession counteredSession = submit(
			startedSession,
			new OfferVector(new BigDecimal("100.00"), 50, 20, 10),
			"Price 100, payment 50, delivery 20, contract 10");

		NegotiationSession clarificationSession = submit(
			counteredSession,
			new OfferVector(new BigDecimal("100.00"), 50, 20, 10),
			"Can you clarify the delivery point?");
		NegotiationDecision clarificationDecision = latestDecision(clarificationSession);

		assertEquals(NegotiationSessionStatus.COUNTERED, clarificationSession.getStatus());
		assertEquals(NegotiationDecisionType.COUNTER, clarificationDecision.getDecision());
		assertNotNull(clarificationDecision.getCounterOffer());
		assertTrue(clarificationDecision.getExplanation().contains("Please confirm which buyer option is closest"));
		long repeatedBuyerOffers = clarificationSession.getOffers().stream()
			.filter(offer -> offer.getParty() == org.GLM.negoriator.domain.NegotiationParty.BUYER)
			.filter(offer -> offer.getRoundNumber() == 2)
			.count();
		assertEquals(3, repeatedBuyerOffers);
	}

	@Test
	void aiFallbackResolvesUnclearReplyIntoBuyerOptionAcceptance() {
		aiGatewayService.setJsonResponse("{\"intentType\":\"ACCEPT_ACTIVE_OFFER\",\"selectedCounterOfferIndex\":1}");

		NegotiationSession startedSession = service.startSession(
			NegotiationDefaults.startSessionCommand(NegotiationStrategy.MESO));

		NegotiationSession counteredSession = submit(
			startedSession,
			new OfferVector(new BigDecimal("100.00"), 50, 20, 10),
			"Price 100, payment 50, delivery 20, contract 10");

		NegotiationOffer optionOne = counteredSession.getOffers().stream()
			.filter(offer -> offer.getParty() == org.GLM.negoriator.domain.NegotiationParty.BUYER)
			.filter(offer -> offer.getRoundNumber() == 1)
			.sorted(Comparator.comparing(org.GLM.negoriator.domain.NegotiationOffer::getCreatedAt))
			.findFirst()
			.orElseThrow();

		NegotiationSession acceptedSession = submit(
			counteredSession,
			new OfferVector(new BigDecimal("100.00"), 50, 20, 10),
			"That arrangement is fine on our side.");
		NegotiationDecision acceptedDecision = latestDecision(acceptedSession);

		assertEquals(NegotiationSessionStatus.ACCEPTED, acceptedSession.getStatus());
		assertEquals(NegotiationDecisionType.ACCEPT, acceptedDecision.getDecision());
		assertNotNull(acceptedDecision.getCounterOffer());
		assertTrue(acceptedDecision.getCounterOffer().toOfferVector().matches(optionOne.toOfferVector()));
	}

	private NegotiationSession submit(NegotiationSession session, OfferVector offer, String message) {
		NegotiationSession updatedSession = service.submitSupplierOffer(session.getId(), offer, null, message);
		return service.getSession(updatedSession.getId());
	}

	private NegotiationDecision latestDecision(NegotiationSession session) {
		return session.getDecisions().stream()
			.reduce((first, second) -> second)
			.orElseThrow();
	}

	@TestConfiguration
	static class TestAiGatewayConfiguration {

		@Bean
		@Primary
		ControlledAiGatewayService controlledAiGatewayService(ObjectMapper objectMapper) {
			return new ControlledAiGatewayService(objectMapper);
		}
	}

	static class ControlledAiGatewayService extends AiGatewayService {

		private String jsonResponse;

		ControlledAiGatewayService(ObjectMapper objectMapper) {
			super(
				RestClient.builder().requestFactory(new JdkClientHttpRequestFactory()),
				objectMapper,
				"ollama",
				"http://localhost:11434",
				"test-model",
				"");
		}

		void setJsonResponse(String jsonResponse) {
			this.jsonResponse = jsonResponse;
		}

		void reset() {
			this.jsonResponse = null;
		}

		@Override
		public String complete(String systemPrompt, String userPrompt) {
			throw new IllegalArgumentException("AI message generation disabled in tests.");
		}

		@Override
		public String completeJson(String systemPrompt, String userPrompt) {
			if (jsonResponse == null) {
				throw new IllegalArgumentException("AI fallback unavailable in tests.");
			}
			return jsonResponse;
		}
	}
}
