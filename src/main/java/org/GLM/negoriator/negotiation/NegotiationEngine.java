package org.GLM.negoriator.negotiation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface NegotiationEngine {

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
		int maxRounds,
		NegotiationState state,
		BigDecimal riskOfWalkaway,
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
