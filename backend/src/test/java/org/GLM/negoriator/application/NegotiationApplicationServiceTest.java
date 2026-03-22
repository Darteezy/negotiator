package org.GLM.negoriator.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.GLM.negoriator.domain.NegotiationParty;
import org.GLM.negoriator.domain.NegotiationSession;
import org.GLM.negoriator.domain.NegotiationSessionRepository;
import org.GLM.negoriator.domain.NegotiationSessionStatus;
import org.GLM.negoriator.domain.NegotiationStrategyChangeTrigger;
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
class NegotiationApplicationServiceTest {

	@Autowired
	private NegotiationApplicationService service;

	@Autowired
	private NegotiationSessionRepository repository;

	@Test
	void submitsSupplierOfferAndPersistsTheFullEngineResponseLoop() {
		NegotiationSession startedSession = service.startSession(new NegotiationApplicationService.StartSessionCommand(
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

		NegotiationSession updatedSession = service.submitSupplierOffer(
			startedSession.getId(),
			new OfferVector(BigDecimal.valueOf(104), 45, 10, 12));

		assertThat(updatedSession.getStatus()).isEqualTo(NegotiationSessionStatus.COUNTERED);
		assertThat(updatedSession.getStrategy()).isEqualTo(NegotiationStrategy.MESO);
		assertThat(updatedSession.getCurrentRound()).isEqualTo(2);
		assertThat(updatedSession.getStrategyChanges()).hasSize(1);
		assertThat(updatedSession.getStrategyChanges().getFirst().getTrigger()).isEqualTo(NegotiationStrategyChangeTrigger.INITIAL_SELECTION);
		assertThat(updatedSession.getOffers()).hasSizeGreaterThanOrEqualTo(2);
		assertThat(updatedSession.getOffers().get(0).getParty()).isEqualTo(NegotiationParty.SUPPLIER);
		assertThat(updatedSession.getOffers().get(1).getParty()).isEqualTo(NegotiationParty.BUYER);
		assertThat(updatedSession.getDecisions()).hasSize(1);
		assertThat(updatedSession.getDecisions().getFirst().getEvaluation().getBuyerUtility()).isNotNull();
		assertThat(updatedSession.getDecisions().getFirst().getCounterOffer()).isNotNull();
		assertThat(updatedSession.getDecisions().getFirst().getReasonCode()).isNotNull();
		assertThat(updatedSession.getDecisions().getFirst().getStrategyUsed()).isEqualTo(NegotiationStrategy.MESO);
		assertThat(updatedSession.getDecisions().getFirst().getStrategyRationale()).isNotBlank();

		NegotiationSession reloadedSession = repository.findById(startedSession.getId()).orElseThrow();
		assertThat(reloadedSession.getStatus()).isEqualTo(NegotiationSessionStatus.COUNTERED);
		assertThat(reloadedSession.getCurrentRound()).isEqualTo(2);
		assertThat(reloadedSession.getStrategy()).isEqualTo(NegotiationStrategy.MESO);
		assertThat(reloadedSession.getStrategyChanges()).hasSize(1);
		assertThat(reloadedSession.getOffers()).hasSizeGreaterThanOrEqualTo(2);
		assertThat(reloadedSession.getDecisions()).hasSize(1);
		assertThat(reloadedSession.getDecisions().getFirst().getSupplierOffer().getParty()).isEqualTo(NegotiationParty.SUPPLIER);
		assertThat(reloadedSession.getDecisions().getFirst().getCounterOffer().getParty()).isEqualTo(NegotiationParty.BUYER);
		assertThat(reloadedSession.getDecisions().getFirst().getEvaluation().getTargetUtility()).isNotNull();
		assertThat(reloadedSession.getDecisions().getFirst().getReasonCode()).isNotNull();
		assertThat(reloadedSession.getDecisions().getFirst().getStrategyUsed()).isEqualTo(NegotiationStrategy.MESO);
	}
}
