package org.GLM.negoriator.negotiation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface NegotiationEngine {

	/** TODO: Add strategy selection here so the engine can negotiate differently by supplier type, risk, or stage. 
	 *
	 * enum NegotiationStrategy {
    MESO, // multiple
    BOULWARE, // slow
    CONCEDER, // fast
    TIT_FOR_TAT // copy opponent
}
	*/

	NegotiationResponse negotiate(NegotiationRequest request);

	enum Decision {
		ACCEPT,
		COUNTER,
		REJECT
	}

	enum NegotiationState {
		PENDING,
		COUNTERED,
		ACCEPTED,
		REJECTED,
		EXPIRED
	}

	enum SupplierArchetype {
		MARGIN_FOCUSED,
		CASHFLOW_FOCUSED,
		OPERATIONS_FOCUSED,
		STABILITY_FOCUSED
	}

	record OfferVector(
		BigDecimal price,
		int paymentDays,
		int deliveryDays,
		int contractMonths
	) {
	}

	record IssueWeights(
		BigDecimal price,
		BigDecimal paymentDays,
		BigDecimal deliveryDays,
		BigDecimal contractMonths
	) {
	}

	// TODO: We have weights here, but there is still no formula that turns one offer into a final score or percent for comparison e.g. 0.78
	record BuyerProfile(
		OfferVector idealOffer,
		OfferVector reservationOffer,
		IssueWeights weights,
		BigDecimal reservationUtility,
		BigDecimal pricePenaltyAlpha,
		BigDecimal priceDeliveryInteractionLambda
	) {
	}

	record SupplierModel(
		Map<SupplierArchetype, BigDecimal> archetypeBeliefs,
		BigDecimal updateSensitivity,
		BigDecimal reservationUtility
	) {
	}

	record NegotiationContext(
		int round,
		// TODO: AI or algoritm should be more agressive in earlier rounds, but with more compromise at later rounds. (Adaptiveness)
		int maxRounds,
		NegotiationState state,
		BigDecimal riskOfWalkaway,
		// TODO: Put who made the offer in the history too so the engine can learn from that as well.
		List<OfferVector> history
	) {
	}

	record NegotiationBounds(
		BigDecimal minPrice,
		BigDecimal maxPrice,
		int minPaymentDays,
		int maxPaymentDays,
		int minDeliveryDays,
		int maxDeliveryDays,
		int minContractMonths,
		int maxContractMonths
	) {
	}

	record NegotiationRequest(
		OfferVector supplierOffer,
		NegotiationContext context,
		BuyerProfile buyerProfile,
		SupplierModel supplierModel,
		NegotiationBounds bounds
	) {
	}

	record OfferEvaluation(
		BigDecimal buyerUtility,
		BigDecimal estimatedSupplierUtility,
		BigDecimal targetUtility,
		BigDecimal continuationValue,
		BigDecimal nashProduct
	) {
	}

	record NegotiationResponse(
		Decision decision,
		NegotiationState nextState,
		List<OfferVector> counterOffers,
		OfferEvaluation evaluation,
		Map<SupplierArchetype, BigDecimal> updatedSupplierBeliefs,
		String explanation
	) {
	}
}
