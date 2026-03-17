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
     */
    public BigDecimal calculate(
            NegotiationEngine.OfferVector offer,
            NegotiationEngine.BuyerProfile profile,
            NegotiationEngine.NegotiationBounds bounds
    ) {

        // TODO:
        // 1. Normalize price
        // 2. Normalize payment days
        // 3. Normalize delivery days
        // 4. Normalize contract months
        // 5. Multiply by issue weights
        // 6. Sum into final utility
        // 7. Optionally apply penalties/interactions
        return BigDecimal.ZERO;
    }

    private BigDecimal normalizePositive(int value, int min, int max) {
        if (max == min) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value - min)
                .divide(BigDecimal.valueOf(max - min), SCALE, RoundingMode.HALF_DOWN);
    }
    private BigDecimal normalizeNegative(int value, int min, int max) {
        if (max == min) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(value - min)
                .divide(BigDecimal.valueOf(max - min), SCALE, RoundingMode.HALF_DOWN);
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
