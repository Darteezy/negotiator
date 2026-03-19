package org.GLM.negoriator.negotiation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DecisionMaker {

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final int SCALE = 4;

    /**
     * @param utility
     * @param profile
     * @param context
     * @return
     */
    public NegotiationEngine.Decision decide(
            BigDecimal utility,
            NegotiationEngine.BuyerProfile profile,
            NegotiationEngine.NegotiationContext context
    ) {
        BigDecimal targetUtility = targetUtility(profile, context);
        BigDecimal hardRejectThreshold = profile.reservationUtility()
                .multiply(HALF)
                .setScale(SCALE, RoundingMode.HALF_UP);

        if (utility.compareTo(targetUtility) >= 0) {
            return NegotiationEngine.Decision.ACCEPT;
        }

        if (utility.compareTo(hardRejectThreshold) < 0) {
            return NegotiationEngine.Decision.REJECT;
        }

        if (context.round() >= context.maxRounds()
                && utility.compareTo(profile.reservationUtility()) < 0) {
            return NegotiationEngine.Decision.REJECT;
        }

        return NegotiationEngine.Decision.COUNTER;
    }

    BigDecimal targetUtility(
            NegotiationEngine.BuyerProfile profile,
            NegotiationEngine.NegotiationContext context
    ) {
        if (context.maxRounds() <= 0) {
            return profile.reservationUtility().setScale(SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal progress = BigDecimal.valueOf(Math.max(0, Math.min(context.round(), context.maxRounds())))
                .divide(BigDecimal.valueOf(context.maxRounds()), SCALE, RoundingMode.HALF_UP);
        BigDecimal stretch = ONE.subtract(profile.reservationUtility())
                .multiply(HALF);

        return profile.reservationUtility()
                .add(stretch.multiply(ONE.subtract(progress)))
                .setScale(SCALE, RoundingMode.HALF_UP);
    }
}
