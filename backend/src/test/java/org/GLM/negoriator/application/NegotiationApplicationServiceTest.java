package org.GLM.negoriator.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.math.BigDecimal;

import org.GLM.negoriator.domain.NegotiationDecision;
import org.GLM.negoriator.domain.NegotiationDecisionType;
import org.GLM.negoriator.domain.NegotiationSession;
import org.GLM.negoriator.domain.NegotiationSessionRepository;
import org.GLM.negoriator.domain.NegotiationSessionStatus;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
		assertEquals(
			"Buyer is ready to close on these terms. Reply with accept to finalize the deal.",
			pendingAcceptanceDecision.getExplanation());
		assertEquals(0, new BigDecimal("120.00").compareTo(pendingAcceptanceDecision.getCounterOffer().toOfferVector().price()));
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
		assertEquals(
			"Accepted because the supplier agreed to the buyer's active offer from the previous round.",
			acceptedDecision.getExplanation());
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
}
