package org.GLM.negoriator.negotiation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class BuyerUtilityCalculator {

    private static final int SCALE = 4;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    private static final BigDecimal ONE = BigDecimal.ONE.setScale(SCALE, RoundingMode.HALF_UP);
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

        NegotiationEngine.IssueWeights normalizedWeights = profile.weights().normalized();

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

        BigDecimal weightedPrice = priceScore.multiply(normalizedWeights.price());
        BigDecimal weightedPayment = paymentScore.multiply(normalizedWeights.paymentDays());
        BigDecimal weightedDelivery = deliveryScore.multiply(normalizedWeights.deliveryDays());
        BigDecimal weightedContract = contractScore.multiply(normalizedWeights.contractMonths());

        BigDecimal utility = weightedPrice
                .add(weightedPayment)
                .add(weightedDelivery)
                .add(weightedContract);

        return clamp(utility);
    }
    BigDecimal normalizePrice(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (max.compareTo(min) == 0) {
            return ZERO;
        }

        BigDecimal normalized = max.subtract(value)
                .divide(max.subtract(min), SCALE, RoundingMode.HALF_UP);

        return clamp(normalized);
    }


    private BigDecimal normalizePositive(int value, int min, int max) {
        if (max == min) {
            return ZERO;
        }

        BigDecimal normalized = BigDecimal.valueOf(value - min)
                .divide(BigDecimal.valueOf(max - min), SCALE, RoundingMode.HALF_UP);

        return clamp(normalized);
    }
    private BigDecimal normalizeNegative(int value, int min, int max) {
        if (max == min) {
            return ZERO;
        }

        BigDecimal normalized = BigDecimal.valueOf(max - value)
                .divide(BigDecimal.valueOf(max - min), SCALE, RoundingMode.HALF_UP);

        return clamp(normalized);
    }
    private BigDecimal clamp(BigDecimal value) {
        BigDecimal scaledValue = value.setScale(SCALE, RoundingMode.HALF_UP);

        if (scaledValue.compareTo(ZERO) < 0) {
            return ZERO;
        }
        if (scaledValue.compareTo(ONE) > 0) {
            return ONE;
        }
        return scaledValue;
    }
}
