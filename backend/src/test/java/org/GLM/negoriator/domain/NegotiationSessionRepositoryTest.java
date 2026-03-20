package org.GLM.negoriator.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.Decision;
import org.GLM.negoriator.negotiation.NegotiationEngine.IssueWeights;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationState;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferEvaluation;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierArchetype;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class NegotiationSessionRepositoryTest {

	@Autowired
	private NegotiationSessionRepository repository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void persistsFullSessionStateSoNegotiationCanBeReconstructed() {
		BuyerProfile buyerProfile = new BuyerProfile(
			new OfferVector(BigDecimal.valueOf(90), 60, 3, 6),
			new OfferVector(BigDecimal.valueOf(120), 30, 14, 24),
			new IssueWeights(
				BigDecimal.valueOf(0.4),
				BigDecimal.valueOf(0.2),
				BigDecimal.valueOf(0.25),
				BigDecimal.valueOf(0.15)),
			BigDecimal.valueOf(0.45),
			BigDecimal.valueOf(2.0),
			BigDecimal.valueOf(0.1));
		NegotiationBounds bounds = new NegotiationBounds(
			BigDecimal.valueOf(80),
			BigDecimal.valueOf(120),
			30,
			90,
			3,
			14,
			3,
			24);
		SupplierModel initialSupplierModel = new SupplierModel(
			Map.of(
				SupplierArchetype.MARGIN_FOCUSED, BigDecimal.valueOf(0.25),
				SupplierArchetype.CASHFLOW_FOCUSED, BigDecimal.valueOf(0.25),
				SupplierArchetype.OPERATIONS_FOCUSED, BigDecimal.valueOf(0.25),
				SupplierArchetype.STABILITY_FOCUSED, BigDecimal.valueOf(0.25)),
			BigDecimal.valueOf(0.50),
			BigDecimal.valueOf(0.35));

		NegotiationSession session = new NegotiationSession(
			2,
			8,
			BigDecimal.valueOf(0.15),
			NegotiationSessionStatus.COUNTERED,
			BuyerProfileSnapshot.from(buyerProfile),
			NegotiationBoundsSnapshot.from(bounds),
			SupplierModelSnapshot.from(initialSupplierModel));

		NegotiationOffer supplierOffer = new NegotiationOffer(
			1,
			NegotiationParty.SUPPLIER,
			OfferTermsSnapshot.from(new OfferVector(BigDecimal.valueOf(104), 45, 10, 12)));
		NegotiationOffer counterOffer = new NegotiationOffer(
			1,
			NegotiationParty.BUYER,
			OfferTermsSnapshot.from(new OfferVector(BigDecimal.valueOf(100), 45, 7, 12)));
		session.addOffer(supplierOffer);
		session.addOffer(counterOffer);

		NegotiationDecision decision = new NegotiationDecision(
			1,
			NegotiationDecisionType.COUNTER,
			NegotiationSessionStatus.COUNTERED,
			supplierOffer,
			counterOffer,
			OfferEvaluationSnapshot.from(new OfferEvaluation(
				BigDecimal.valueOf(0.6400),
				BigDecimal.valueOf(0.5200),
				BigDecimal.valueOf(0.7800),
				BigDecimal.valueOf(0.6630),
				BigDecimal.valueOf(0.0364))),
			SupplierBeliefSnapshot.from(Map.of(
				SupplierArchetype.MARGIN_FOCUSED, BigDecimal.valueOf(0.35),
				SupplierArchetype.CASHFLOW_FOCUSED, BigDecimal.valueOf(0.20),
				SupplierArchetype.OPERATIONS_FOCUSED, BigDecimal.valueOf(0.30),
				SupplierArchetype.STABILITY_FOCUSED, BigDecimal.valueOf(0.15))),
			"Countered by improving delivery.");
		session.addDecision(decision);

		NegotiationSession saved = repository.saveAndFlush(session);
		entityManager.clear();
		NegotiationSession reloaded = repository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.toBuyerProfile().idealOffer().price()).isEqualByComparingTo(buyerProfile.idealOffer().price());
		assertThat(reloaded.toBuyerProfile().reservationOffer().price()).isEqualByComparingTo(buyerProfile.reservationOffer().price());
		assertThat(reloaded.toBuyerProfile().weights().price()).isEqualByComparingTo(buyerProfile.weights().price());
		assertThat(reloaded.toBuyerProfile().reservationUtility()).isEqualByComparingTo(buyerProfile.reservationUtility());
		assertThat(reloaded.toNegotiationBounds().minPrice()).isEqualByComparingTo(bounds.minPrice());
		assertThat(reloaded.toNegotiationBounds().maxPrice()).isEqualByComparingTo(bounds.maxPrice());
		assertThat(reloaded.toNegotiationBounds().minPaymentDays()).isEqualTo(bounds.minPaymentDays());
		assertThat(reloaded.toNegotiationBounds().maxContractMonths()).isEqualTo(bounds.maxContractMonths());
		assertThat(reloaded.initialSupplierModel().archetypeBeliefs().get(SupplierArchetype.MARGIN_FOCUSED)).isEqualByComparingTo(
			initialSupplierModel.archetypeBeliefs().get(SupplierArchetype.MARGIN_FOCUSED));
		assertThat(reloaded.initialSupplierModel().archetypeBeliefs().get(SupplierArchetype.OPERATIONS_FOCUSED)).isEqualByComparingTo(
			initialSupplierModel.archetypeBeliefs().get(SupplierArchetype.OPERATIONS_FOCUSED));
		assertThat(reloaded.initialSupplierModel().updateSensitivity()).isEqualByComparingTo(initialSupplierModel.updateSensitivity());
		assertThat(reloaded.initialSupplierModel().reservationUtility()).isEqualByComparingTo(initialSupplierModel.reservationUtility());
		assertThat(reloaded.currentSupplierModel().archetypeBeliefs().get(SupplierArchetype.MARGIN_FOCUSED)).isEqualByComparingTo("0.35");
		assertThat(reloaded.currentSupplierModel().archetypeBeliefs().get(SupplierArchetype.CASHFLOW_FOCUSED)).isEqualByComparingTo("0.20");
		assertThat(reloaded.currentSupplierModel().archetypeBeliefs().get(SupplierArchetype.OPERATIONS_FOCUSED)).isEqualByComparingTo("0.30");
		assertThat(reloaded.currentSupplierModel().archetypeBeliefs().get(SupplierArchetype.STABILITY_FOCUSED)).isEqualByComparingTo("0.15");
		assertThat(reloaded.currentSupplierModel().updateSensitivity()).isEqualByComparingTo("0.5");
		assertThat(reloaded.currentSupplierModel().reservationUtility()).isEqualByComparingTo("0.35");
		assertThat(reloaded.toNegotiationContext().round()).isEqualTo(2);
		assertThat(reloaded.toNegotiationContext().state()).isEqualTo(NegotiationState.COUNTERED);
		assertThat(reloaded.toNegotiationContext().history()).hasSize(2);
		assertThat(reloaded.toNegotiationContext().history().get(0).price()).isEqualByComparingTo("104");
		assertThat(reloaded.toNegotiationContext().history().get(0).deliveryDays()).isEqualTo(10);
		assertThat(reloaded.toNegotiationContext().history().get(1).price()).isEqualByComparingTo("100");
		assertThat(reloaded.toNegotiationContext().history().get(1).deliveryDays()).isEqualTo(7);
		assertThat(reloaded.getDecisions()).hasSize(1);
		assertThat(reloaded.getDecisions().getFirst().getDecision().toDecision()).isEqualTo(Decision.COUNTER);
		assertThat(reloaded.getDecisions().getFirst().getResultingStatus().toNegotiationState()).isEqualTo(NegotiationState.COUNTERED);
		assertThat(reloaded.getDecisions().getFirst().toOfferEvaluation().buyerUtility()).isEqualByComparingTo("0.6400");
		assertThat(reloaded.getDecisions().getFirst().getCounterOffer().toOfferVector().price()).isEqualByComparingTo("100");
		assertThat(reloaded.getDecisions().getFirst().getCounterOffer().toOfferVector().deliveryDays()).isEqualTo(7);
	}
}
