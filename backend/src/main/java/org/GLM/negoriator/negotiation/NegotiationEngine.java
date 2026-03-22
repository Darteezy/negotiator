package org.GLM.negoriator.negotiation;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

	enum NegotiationStrategy {
		BASELINE,
		MESO,
		BOULWARE,
		CONCEDER,
		TIT_FOR_TAT
	}

	enum SupplierArchetype {
		MARGIN_FOCUSED,
		CASHFLOW_FOCUSED,
		OPERATIONS_FOCUSED,
		STABILITY_FOCUSED
	}

	enum NegotiationIssue {
		PRICE,
		PAYMENT_DAYS,
		DELIVERY_DAYS,
		CONTRACT_MONTHS
	}

	enum DecisionReason {
		TARGET_UTILITY_MET,
		OUTSIDE_RESERVATION_LIMITS,
		BELOW_HARD_REJECT_THRESHOLD,
		FINAL_ROUND_BELOW_RESERVATION,
		COUNTER_TO_CLOSE_GAP
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
		private static final int SCALE = 8;

		IssueWeights normalized() {
			if (price.compareTo(BigDecimal.ZERO) < 0
				|| paymentDays.compareTo(BigDecimal.ZERO) < 0
				|| deliveryDays.compareTo(BigDecimal.ZERO) < 0
				|| contractMonths.compareTo(BigDecimal.ZERO) < 0) {
				throw new IllegalArgumentException("Issue weights cannot be negative.");
			}

			BigDecimal totalWeight = totalWeight();
			if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
				throw new IllegalArgumentException("Issue weights must sum to more than zero.");
			}

			return new IssueWeights(
				price.divide(totalWeight, SCALE, RoundingMode.HALF_UP),
				paymentDays.divide(totalWeight, SCALE, RoundingMode.HALF_UP),
				deliveryDays.divide(totalWeight, SCALE, RoundingMode.HALF_UP),
				contractMonths.divide(totalWeight, SCALE, RoundingMode.HALF_UP));
		}

		BigDecimal totalWeight() {
			return price
				.add(paymentDays)
				.add(deliveryDays)
				.add(contractMonths);
		}
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
		NegotiationStrategy strategy,
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
		DecisionReason reasonCode,
		NegotiationIssue focusIssue,
		String explanation
	) {
	}
}
