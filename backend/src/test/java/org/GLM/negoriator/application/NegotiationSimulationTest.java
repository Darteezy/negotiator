package org.GLM.negoriator.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.GLM.negoriator.domain.NegotiationParty;
import org.GLM.negoriator.domain.NegotiationSession;
import org.GLM.negoriator.domain.NegotiationSessionStatus;
import org.GLM.negoriator.domain.SupplierConstraintsSnapshot;
import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.IssueWeights;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierArchetype;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierModel;
import org.GLM.negoriator.negotiation.NegotiationEngineImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({NegotiationApplicationService.class, NegotiationEngineImpl.class})
class NegotiationSimulationTest {

	@Autowired
	private NegotiationApplicationService service;

	@Test
	void simulatesNegotiationAgainstSupplierWithPersistentPriceFloor() {
		NegotiationSession session = service.startSession(new NegotiationApplicationService.StartSessionCommand(
			NegotiationStrategy.MESO,
			8,
			BigDecimal.valueOf(0.15),
			new BuyerProfile(
				new OfferVector(BigDecimal.valueOf(90), 60, 3, 6),
				new OfferVector(BigDecimal.valueOf(120), 30, 14, 24),
				new IssueWeights(
					BigDecimal.valueOf(0.4),
					BigDecimal.valueOf(0.2),
					BigDecimal.valueOf(0.25),
					BigDecimal.valueOf(0.15)),
				BigDecimal.valueOf(0.45),
				BigDecimal.valueOf(2.0),
				BigDecimal.valueOf(0.1)),
			new NegotiationBounds(
				BigDecimal.valueOf(80),
				BigDecimal.valueOf(120),
				30,
				90,
				3,
				14,
				3,
				24),
			new SupplierModel(
				Map.of(
					SupplierArchetype.MARGIN_FOCUSED, BigDecimal.valueOf(0.25),
					SupplierArchetype.CASHFLOW_FOCUSED, BigDecimal.valueOf(0.25),
					SupplierArchetype.OPERATIONS_FOCUSED, BigDecimal.valueOf(0.25),
					SupplierArchetype.STABILITY_FOCUSED, BigDecimal.valueOf(0.25)),
				BigDecimal.valueOf(0.50),
				BigDecimal.valueOf(0.35))));

		NegotiationSession afterRoundOne = service.submitSupplierOffer(
			session.getId(),
			new OfferVector(BigDecimal.valueOf(104), 45, 10, 12));

		assertThat(afterRoundOne.getStatus()).isEqualTo(NegotiationSessionStatus.COUNTERED);

		NegotiationSession afterRoundTwo = service.submitSupplierOffer(
			session.getId(),
			new OfferVector(BigDecimal.valueOf(104), 45, 10, 12),
			new SupplierConstraintsSnapshot(BigDecimal.valueOf(101), null, null, null));

		List<OfferVector> buyerRoundTwoOptions = afterRoundTwo.getOffers().stream()
			.filter(offer -> offer.getParty() == NegotiationParty.BUYER)
			.filter(offer -> offer.getRoundNumber().equals(2))
			.map(offer -> offer.toOfferVector())
			.toList();

		assertThat(afterRoundTwo.getStatus()).isEqualTo(NegotiationSessionStatus.COUNTERED);
		assertThat(afterRoundTwo.getSupplierConstraintsSnapshot().getPriceFloor()).isEqualByComparingTo("101");
		assertThat(buyerRoundTwoOptions).isNotEmpty();
		assertThat(buyerRoundTwoOptions)
			.allSatisfy(offer -> assertThat(offer.price()).isGreaterThanOrEqualTo(BigDecimal.valueOf(101)));

		OfferVector acceptedOffer = buyerRoundTwoOptions.getFirst();
		NegotiationSession acceptedSession = service.submitSupplierOffer(session.getId(), acceptedOffer);

		assertThat(acceptedSession.getStatus()).isEqualTo(NegotiationSessionStatus.ACCEPTED);
		assertThat(acceptedSession.isClosed()).isTrue();
		assertThat(acceptedSession.getDecisions()).hasSize(3);
	}
}