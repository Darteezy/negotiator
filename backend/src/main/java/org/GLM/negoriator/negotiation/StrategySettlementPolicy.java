package org.GLM.negoriator.negotiation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationContext;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationRequest;

final class StrategySettlementPolicy {

	private static final int SCALE = 4;
	private static final BigDecimal ZERO = BigDecimal.ZERO;
	private static final BigDecimal ONE = BigDecimal.ONE;
	private static final BigDecimal BASELINE_PRICE_RATIO = new BigDecimal("0.70");
	private static final BigDecimal MESO_PRICE_RATIO = new BigDecimal("0.78");
	private static final BigDecimal BOULWARE_PRICE_RATIO = new BigDecimal("0.55");
	private static final BigDecimal CONCEDER_PRICE_RATIO = ONE;
	private static final BigDecimal TIT_FOR_TAT_BASE_PRICE_RATIO = new BigDecimal("0.62");
	private static final BigDecimal TIT_FOR_TAT_PRICE_BONUS_RANGE = new BigDecimal("0.18");
	private static final BigDecimal BASELINE_UTILITY_FLOOR = new BigDecimal("0.6200");
	private static final BigDecimal MESO_UTILITY_FLOOR = new BigDecimal("0.6000");
	private static final BigDecimal BOULWARE_UTILITY_FLOOR = new BigDecimal("0.7100");
	private static final BigDecimal CONCEDER_UTILITY_FLOOR = new BigDecimal("0.5000");
	private static final BigDecimal TIT_FOR_TAT_BASE_UTILITY_FLOOR = new BigDecimal("0.6200");
	private static final BigDecimal TIT_FOR_TAT_UTILITY_RELIEF_RANGE = new BigDecimal("0.0600");

	private final BuyerUtilityCalculator utilityCalculator;

	StrategySettlementPolicy(BuyerUtilityCalculator utilityCalculator) {
		this.utilityCalculator = utilityCalculator;
	}

	SettlementThresholds thresholds(NegotiationRequest request) {
		BuyerProfile buyerProfile = request.buyerProfile();
		NegotiationContext context = request.context();
		BigDecimal concessionFraction = supplierConcessionFraction(
			request.supplierOffer(),
			buyerProfile,
			request.bounds(),
			context);
		BigDecimal minimumUtility = normalizedReservationUtility(buyerProfile).max(strategyUtilityFloor(context.strategy(), concessionFraction));
		BigDecimal maximumPrice = settlementPriceCeiling(
			buyerProfile,
			context.strategy(),
			concessionFraction);

		return new SettlementThresholds(
			minimumUtility.setScale(SCALE, RoundingMode.HALF_UP),
			maximumPrice.setScale(2, RoundingMode.HALF_UP));
	}

	boolean allowsAcceptance(
		OfferVector offer,
		BigDecimal utility,
		SettlementThresholds thresholds
	) {
		return utility.compareTo(thresholds.minimumUtility()) >= 0
			&& offer.price().compareTo(thresholds.maximumPrice()) <= 0;
	}

	OfferVector clampCounterOffer(
		OfferVector counterOffer,
		SettlementThresholds thresholds
	) {
		BigDecimal cappedPrice = counterOffer.price()
			.min(thresholds.maximumPrice())
			.setScale(2, RoundingMode.HALF_UP);
		if (cappedPrice.compareTo(counterOffer.price()) == 0) {
			return counterOffer;
		}

		return new OfferVector(
			cappedPrice,
			counterOffer.paymentDays(),
			counterOffer.deliveryDays(),
			counterOffer.contractMonths());
	}

	private BigDecimal settlementPriceCeiling(
		BuyerProfile buyerProfile,
		NegotiationStrategy strategy,
		BigDecimal concessionFraction
	) {
		BigDecimal ratio = switch (strategy) {
			case BASELINE -> BASELINE_PRICE_RATIO;
			case MESO -> MESO_PRICE_RATIO;
			case BOULWARE -> BOULWARE_PRICE_RATIO;
			case CONCEDER -> CONCEDER_PRICE_RATIO;
			case TIT_FOR_TAT -> TIT_FOR_TAT_BASE_PRICE_RATIO
				.add(TIT_FOR_TAT_PRICE_BONUS_RANGE.multiply(concessionFraction));
		};
		BigDecimal priceSpan = BuyerPreferenceScoring.priceSpan(buyerProfile);
		BigDecimal cappedRatio = clampUnitInterval(ratio);
		return buyerProfile.idealOffer().price()
			.add(priceSpan.multiply(cappedRatio))
			.min(buyerProfile.reservationOffer().price())
			.max(buyerProfile.idealOffer().price());
	}

	private BigDecimal strategyUtilityFloor(
		NegotiationStrategy strategy,
		BigDecimal concessionFraction
	) {
		return switch (strategy) {
			case BASELINE -> BASELINE_UTILITY_FLOOR;
			case MESO -> MESO_UTILITY_FLOOR;
			case BOULWARE -> BOULWARE_UTILITY_FLOOR;
			case CONCEDER -> CONCEDER_UTILITY_FLOOR;
			case TIT_FOR_TAT -> TIT_FOR_TAT_BASE_UTILITY_FLOOR
				.subtract(TIT_FOR_TAT_UTILITY_RELIEF_RANGE.multiply(concessionFraction));
		};
	}

	private BigDecimal supplierConcessionFraction(
		OfferVector currentSupplierOffer,
		BuyerProfile buyerProfile,
		NegotiationBounds bounds,
		NegotiationContext context
	) {
		OfferVector anchorOffer = supplierHistory(context, currentSupplierOffer).getFirst();
		BigDecimal anchorUtility = utilityCalculator.calculate(anchorOffer, buyerProfile, bounds);
		BigDecimal currentUtility = utilityCalculator.calculate(currentSupplierOffer, buyerProfile, bounds);
		if (currentUtility.compareTo(anchorUtility) <= 0) {
			return ZERO.setScale(SCALE, RoundingMode.HALF_UP);
		}

		OfferVector bestNonPricePackageAtAnchorPrice = new OfferVector(
			anchorOffer.price(),
			buyerProfile.idealOffer().paymentDays(),
			buyerProfile.idealOffer().deliveryDays(),
			buyerProfile.idealOffer().contractMonths());
		BigDecimal maximumUtility = utilityCalculator.calculate(bestNonPricePackageAtAnchorPrice, buyerProfile, bounds);
		BigDecimal possibleGain = maximumUtility.subtract(anchorUtility);
		if (possibleGain.compareTo(ZERO) <= 0) {
			return ZERO.setScale(SCALE, RoundingMode.HALF_UP);
		}

		return currentUtility.subtract(anchorUtility)
			.divide(possibleGain, SCALE + 2, RoundingMode.HALF_UP)
			.max(ZERO)
			.min(ONE)
			.setScale(SCALE, RoundingMode.HALF_UP);
	}

	private List<OfferVector> supplierHistory(
		NegotiationContext context,
		OfferVector currentSupplierOffer
	) {
		List<OfferVector> supplierHistory = new ArrayList<>();
		List<OfferVector> history = context.history();

		for (int index = 0; index < history.size(); index += 2) {
			supplierHistory.add(history.get(index));
		}

		supplierHistory.add(currentSupplierOffer);
		return supplierHistory;
	}

	private BigDecimal normalizedReservationUtility(BuyerProfile buyerProfile) {
		if (buyerProfile.reservationUtility() == null) {
			return ZERO.setScale(SCALE, RoundingMode.HALF_UP);
		}

		return buyerProfile.reservationUtility()
			.max(ZERO)
			.min(ONE)
			.setScale(SCALE, RoundingMode.HALF_UP);
	}

	private BigDecimal clampUnitInterval(BigDecimal value) {
		return value.max(ZERO).min(ONE).setScale(SCALE, RoundingMode.HALF_UP);
	}

	record SettlementThresholds(
		BigDecimal minimumUtility,
		BigDecimal maximumPrice
	) {
	}
}
