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
            BigDecimal hardRejectThreshold = hardRejectThreshold(context);

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

            double progress = normalizedProgress(context);
            double target = switch (context.strategy()) {
                case BASELINE -> 1.0d - progress;
                case MESO -> 1.0d - Math.pow(progress, 1.35d);
                case BOULWARE -> 1.0d - Math.pow(progress, 2.4d);
                case CONCEDER -> 1.0d - Math.sqrt(progress);
                case TIT_FOR_TAT -> titForTatTarget(progress, context);
            };

            return clampUnitInterval(target);
        }

        private BigDecimal hardRejectThreshold(NegotiationEngine.NegotiationContext context) {
            double threshold = switch (context.strategy()) {
                case BASELINE -> -0.0500d;
                case MESO -> -0.0600d;
                case BOULWARE -> -0.0350d;
                case CONCEDER -> -0.0800d;
                case TIT_FOR_TAT -> titForTatRejectThreshold(context);
            };

            return BigDecimal.valueOf(threshold).setScale(SCALE, RoundingMode.HALF_UP);
        }

        private double normalizedProgress(NegotiationEngine.NegotiationContext context) {
            return Math.max(1, Math.min(context.round(), context.maxRounds())) / (double) context.maxRounds();
        }

        private double titForTatTarget(double progress, NegotiationEngine.NegotiationContext context) {
            double baseline = 1.0d - progress;
            double reciprocityBonus = recentSupplierConcessionScore(context);
            double firmnessPenalty = reciprocityBonus == 0.0d && supplierOfferCount(context) >= 2 ? 0.0400d : 0.0d;
            return baseline - reciprocityBonus + firmnessPenalty;
        }

        private double titForTatRejectThreshold(NegotiationEngine.NegotiationContext context) {
            double reciprocityBonus = recentSupplierConcessionScore(context);
            return reciprocityBonus > 0.0d ? -0.0650d : -0.0400d;
        }

        private double recentSupplierConcessionScore(NegotiationEngine.NegotiationContext context) {
            java.util.List<NegotiationEngine.OfferVector> supplierHistory = supplierHistory(context);
            if (supplierHistory.size() < 2) {
                return 0.0d;
            }

            NegotiationEngine.OfferVector previous = supplierHistory.get(supplierHistory.size() - 2);
            NegotiationEngine.OfferVector current = supplierHistory.get(supplierHistory.size() - 1);
            double score = 0.0d;

            if (current.price().compareTo(previous.price()) < 0) {
                score += 0.0400d;
            }
            if (current.paymentDays() > previous.paymentDays()) {
                score += 0.0250d;
            }
            if (current.deliveryDays() < previous.deliveryDays()) {
                score += 0.0250d;
            }
            if (current.contractMonths() < previous.contractMonths()) {
                score += 0.0200d;
            }

            return Math.min(score, 0.1100d);
        }

        private int supplierOfferCount(NegotiationEngine.NegotiationContext context) {
            return supplierHistory(context).size();
        }

        private java.util.List<NegotiationEngine.OfferVector> supplierHistory(NegotiationEngine.NegotiationContext context) {
            java.util.List<NegotiationEngine.OfferVector> history = context.history();
            java.util.List<NegotiationEngine.OfferVector> supplierHistory = new java.util.ArrayList<>();

            for (int index = 0; index < history.size(); index += 2) {
                supplierHistory.add(history.get(index));
            }

            return supplierHistory;
        }

        private BigDecimal clampUnitInterval(double value) {
            return BigDecimal.valueOf(Math.max(0.0d, Math.min(1.0d, value)))
                .setScale(SCALE, RoundingMode.HALF_UP);
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
