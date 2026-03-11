package org.GLM.negoriator.negotiation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.Decision;
import org.GLM.negoriator.negotiation.NegotiationEngine.IssueWeights;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationContext;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationRequest;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationResponse;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationState;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierArchetype;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierModel;
import org.junit.jupiter.api.Test;

class NegotiationEngineTest {

	@Test
	void requestAndResponseContractsAreConstructible() {
		NegotiationRequest request = new NegotiationRequest(
			new OfferVector(BigDecimal.valueOf(105), 45, 7, 12),
			new NegotiationContext(2, 8, NegotiationState.PENDING, BigDecimal.valueOf(0.05), List.of()),
			new BuyerProfile(
				new OfferVector(BigDecimal.valueOf(90), 90, 3, 6),
				new OfferVector(BigDecimal.valueOf(120), 30, 14, 24),
				new IssueWeights(
					BigDecimal.valueOf(0.4),
					BigDecimal.valueOf(0.2),
					BigDecimal.valueOf(0.25),
					BigDecimal.valueOf(0.15)),
				BigDecimal.valueOf(0.45),
				BigDecimal.valueOf(2),
				BigDecimal.valueOf(0.1)),
			new SupplierModel(
				Map.of(
					SupplierArchetype.MARGIN_FOCUSED, BigDecimal.valueOf(0.25),
					SupplierArchetype.CASHFLOW_FOCUSED, BigDecimal.valueOf(0.25),
					SupplierArchetype.OPERATIONS_FOCUSED, BigDecimal.valueOf(0.25),
					SupplierArchetype.STABILITY_FOCUSED, BigDecimal.valueOf(0.25)),
				BigDecimal.valueOf(0.5),
				BigDecimal.valueOf(0.35)),
			new NegotiationBounds(BigDecimal.valueOf(80), BigDecimal.valueOf(120), 30, 90, 3, 14, 3, 24));

		NegotiationResponse response = new NegotiationResponse(
			Decision.COUNTER,
			NegotiationState.COUNTERED,
			List.of(new OfferVector(BigDecimal.valueOf(103), 60, 9, 9)),
			new NegotiationEngine.OfferEvaluation(
				BigDecimal.valueOf(0.71),
				BigDecimal.valueOf(0.48),
				BigDecimal.valueOf(0.82),
				BigDecimal.valueOf(0.77),
				BigDecimal.valueOf(0.13)),
			Map.of(
				SupplierArchetype.MARGIN_FOCUSED, BigDecimal.valueOf(0.30),
				SupplierArchetype.CASHFLOW_FOCUSED, BigDecimal.valueOf(0.20),
				SupplierArchetype.OPERATIONS_FOCUSED, BigDecimal.valueOf(0.25),
				SupplierArchetype.STABILITY_FOCUSED, BigDecimal.valueOf(0.25)),
			"Contract only");

		assertThat(request.supplierOffer().price()).isEqualByComparingTo("105");
		assertThat(request.context().state()).isEqualTo(NegotiationState.PENDING);
		assertThat(response.decision()).isEqualTo(Decision.COUNTER);
		assertThat(response.nextState()).isEqualTo(NegotiationState.COUNTERED);
		assertThat(response.counterOffers()).hasSize(1);
		assertThat(response.evaluation().buyerUtility()).isEqualByComparingTo("0.71");
		assertThat(response.updatedSupplierBeliefs()).containsKeys(SupplierArchetype.values());
		assertThat(response.explanation()).isEqualTo("Contract only");
	}
}
