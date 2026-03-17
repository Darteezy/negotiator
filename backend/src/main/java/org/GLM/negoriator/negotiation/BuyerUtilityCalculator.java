package org.GLM.negoriator.negotiation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class BuyerUtilityCalculator {

    private static final int SCALE = 4;
    /**
     * Calculates the buyer-side utility score for a supplier offer.
     * Expected output:
     * - normalized weighted score
     * - typically in range 0.0 to 1.0
     * - higher means better for the buyer
     * @return - price + payment + delivery + contract ≈ 1.0
     */
    public BigDecimal calculate(
            NegotiationEngine.OfferVector offer,
            NegotiationEngine.BuyerProfile profile,
            NegotiationEngine.NegotiationBounds bounds
    ) {

        BigDecimal priceScore = normalizePrice(
                offer.price(),
                bounds.minPrice(),
                bounds.maxPrice()
        );
        BigDecimal paymentScore = normalizePositive(
                offer.paymentDays(),
                bounds.minPaymentDays(),
                bounds.maxPaymentDays()
        );
        BigDecimal deliveryScore = normalizeNegative(
                offer.deliveryDays(),
                bounds.minDeliveryDays(),
                bounds.maxDeliveryDays()
        );

        BigDecimal contractScore = normalizeNegative(
                offer.contractMonths(),
                bounds.minContractMonths(),
                bounds.maxContractMonths()
        );

        BigDecimal weightedPrice = priceScore.multiply(profile.weights().price());
        BigDecimal weightedPayment = paymentScore.multiply(profile.weights().paymentDays());
        BigDecimal weightedDelivery = deliveryScore.multiply(profile.weights().deliveryDays());
        BigDecimal weightedContract = contractScore.multiply(profile.weights().contractMonths());

        BigDecimal utility = weightedPrice
                .add(weightedPayment)
                .add(weightedDelivery)
                .add(weightedContract);

        return utility.setScale(SCALE, RoundingMode.HALF_UP);
    }

    BigDecimal normalizePrice(BigDecimal value, BigDecimal min, BigDecimal max) {
        return max.subtract(value)
                .divide(max.subtract(min), RoundingMode.HALF_UP);
    }


    private BigDecimal normalizePositive(int value, int min, int max) {
        if (max == min) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(value - min)
                .divide(BigDecimal.valueOf(max - min), SCALE, RoundingMode.HALF_UP);
    }
    private BigDecimal normalizeNegative(int value, int min, int max) {
        if (max == min) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(max - value)
                .divide(BigDecimal.valueOf(max - min), SCALE, RoundingMode.HALF_UP);
    }
    private BigDecimal normalizeNegative(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (max.compareTo(min) == 0)
        {
            return BigDecimal.ZERO;
        }
        return max.subtract(value)
                .divide(max.subtract(min), SCALE, RoundingMode.HALF_UP);
    }
}
