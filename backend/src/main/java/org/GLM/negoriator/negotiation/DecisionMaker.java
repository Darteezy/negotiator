package org.GLM.negoriator.negotiation;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.GLM.negoriator.negotiation.NegotiationEngine.Decision;
import org.GLM.negoriator.negotiation.NegotiationEngine.DecisionReason;

public class DecisionMaker {

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal ZERO = BigDecimal.ZERO;
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
        return evaluate(utility, profile, context).decision();
    }

	DecisionOutcome evaluate(
			BigDecimal utility,
			NegotiationEngine.BuyerProfile profile,
			NegotiationEngine.NegotiationContext context
	) {
	        BigDecimal reservationUtility = normalizedReservationUtility(profile);
	        BigDecimal targetUtility = targetUtility(profile, context).max(reservationUtility);
	        BigDecimal hardRejectThreshold = ZERO.setScale(SCALE, RoundingMode.HALF_UP);

	        if (utility.compareTo(hardRejectThreshold) < 0) {
	            return new DecisionOutcome(Decision.REJECT, targetUtility, hardRejectThreshold, DecisionReason.BELOW_HARD_REJECT_THRESHOLD);
	        }

	        if (utility.compareTo(targetUtility) >= 0) {
	            return new DecisionOutcome(Decision.ACCEPT, targetUtility, hardRejectThreshold, DecisionReason.TARGET_UTILITY_MET);
	        }

	        if (context.round() >= context.maxRounds()) {
	            return new DecisionOutcome(Decision.REJECT, targetUtility, hardRejectThreshold, DecisionReason.FINAL_ROUND_BELOW_RESERVATION);
	        }

	        return new DecisionOutcome(Decision.COUNTER, targetUtility, hardRejectThreshold, DecisionReason.COUNTER_TO_CLOSE_GAP);
	    }

    BigDecimal targetUtility(
            NegotiationEngine.BuyerProfile profile,
            NegotiationEngine.NegotiationContext context
    ) {
        if (context.maxRounds() <= 0) {
            return ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal progress = BigDecimal.valueOf(Math.max(1, Math.min(context.round(), context.maxRounds())))
                .divide(BigDecimal.valueOf(context.maxRounds()), SCALE, RoundingMode.HALF_UP);
	        return ONE.subtract(progress).max(ZERO).setScale(SCALE, RoundingMode.HALF_UP);
	    }

	    private BigDecimal normalizedReservationUtility(NegotiationEngine.BuyerProfile profile) {
	        BigDecimal reservationUtility = profile.reservationUtility();
	        if (reservationUtility == null) {
	            return ZERO.setScale(SCALE, RoundingMode.HALF_UP);
	        }

	        return reservationUtility.max(ZERO).min(ONE).setScale(SCALE, RoundingMode.HALF_UP);
	    }

    record DecisionOutcome(
            Decision decision,
            BigDecimal targetUtility,
            BigDecimal hardRejectThreshold,
            DecisionReason reasonCode
    ) {
    }
}
