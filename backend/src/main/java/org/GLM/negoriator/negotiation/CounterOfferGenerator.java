package org.GLM.negoriator.negotiation;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CounterOfferGenerator {

    private static final int SCALE = 4;

    /**
     *
     * @param profile
     * @param context
     * @param bounds
     * @param supplierOffer
     * @return - new offer Which term in the supplier offer hurts the buyer the most?
     */
    public NegotiationEngine.OfferVector counterOffer(
            NegotiationEngine.BuyerProfile profile,
            NegotiationEngine.NegotiationContext context,
            NegotiationEngine.NegotiationBounds bounds,
            NegotiationEngine.OfferVector supplierOffer) {
        String issueToImprove = issueToImprove(profile, bounds, supplierOffer);
        NegotiationEngine.OfferVector idealOffer = profile.idealOffer();

        BigDecimal price = supplierOffer.price();
        int paymentDays = supplierOffer.paymentDays();
        int deliveryDays = supplierOffer.deliveryDays();
        int contractMonths = supplierOffer.contractMonths();

        switch (issueToImprove) {
            case "payment" -> paymentDays = moveIntTowards(
                    supplierOffer.paymentDays(),
                    idealOffer.paymentDays(),
                    bounds.minPaymentDays(),
                    bounds.maxPaymentDays());
            case "delivery" -> deliveryDays = moveIntTowards(
                    supplierOffer.deliveryDays(),
                    idealOffer.deliveryDays(),
                    bounds.minDeliveryDays(),
                    bounds.maxDeliveryDays());
            case "contract" -> contractMonths = moveIntTowards(
                    supplierOffer.contractMonths(),
                    idealOffer.contractMonths(),
                    bounds.minContractMonths(),
                    bounds.maxContractMonths());
            default -> price = moveDecimalTowards(
                    supplierOffer.price(),
                    idealOffer.price(),
                    bounds.minPrice(),
                    bounds.maxPrice());
        }

        return new NegotiationEngine.OfferVector(price, paymentDays, deliveryDays, contractMonths);
    }

    String issueToImprove(
            NegotiationEngine.BuyerProfile profile,
            NegotiationEngine.NegotiationBounds bounds,
            NegotiationEngine.OfferVector supplierOffer
    ) {
        NegotiationEngine.OfferVector ideal = profile.idealOffer();
        NegotiationEngine.IssueWeights weights = profile.weights();

        BigDecimal priceGap = weightedGap(
                positiveGap(supplierOffer.price(), ideal.price()),
                bounds.maxPrice().subtract(bounds.minPrice()),
                weights.price());
        BigDecimal paymentGap = weightedGap(
                BigDecimal.valueOf(Math.max(0, ideal.paymentDays() - supplierOffer.paymentDays())),
                BigDecimal.valueOf(bounds.maxPaymentDays() - bounds.minPaymentDays()),
                weights.paymentDays());
        BigDecimal deliveryGap = weightedGap(
                BigDecimal.valueOf(Math.max(0, supplierOffer.deliveryDays() - ideal.deliveryDays())),
                BigDecimal.valueOf(bounds.maxDeliveryDays() - bounds.minDeliveryDays()),
                weights.deliveryDays());
        BigDecimal contractGap = weightedGap(
                BigDecimal.valueOf(Math.max(0, supplierOffer.contractMonths() - ideal.contractMonths())),
                BigDecimal.valueOf(bounds.maxContractMonths() - bounds.minContractMonths()),
                weights.contractMonths());

        String worstIssue = "price";
        BigDecimal worstGap = priceGap;

        if (paymentGap.compareTo(worstGap) > 0) {
            worstIssue = "payment";
            worstGap = paymentGap;
        }
        if (deliveryGap.compareTo(worstGap) > 0) {
            worstIssue = "delivery";
            worstGap = deliveryGap;
        }
        if (contractGap.compareTo(worstGap) > 0) {
            worstIssue = "contract";
        }

        return worstIssue;
    }

    private BigDecimal weightedGap(BigDecimal gap, BigDecimal span, BigDecimal weight) {
        if (span.compareTo(BigDecimal.ZERO) <= 0 || gap.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        return gap.divide(span, SCALE, RoundingMode.HALF_UP)
                .multiply(weight);
    }

    private BigDecimal positiveGap(BigDecimal value, BigDecimal ideal) {
        return value.subtract(ideal).max(BigDecimal.ZERO);
    }

    private BigDecimal moveDecimalTowards(BigDecimal current, BigDecimal target, BigDecimal min, BigDecimal max) {
        if (current.compareTo(target) == 0) {
            return clampDecimal(current, min, max);
        }

        BigDecimal moved = current.add(target)
                .divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);

        if (moved.compareTo(current) == 0) {
            moved = moved.add(target.compareTo(current) < 0 ? new BigDecimal("-0.01") : new BigDecimal("0.01"));
        }

        return clampDecimal(moved, min, max);
    }

    private int moveIntTowards(int current, int target, int min, int max) {
        if (current == target) {
            return clampInt(current, min, max);
        }

        int moved = current + ((target - current) / 2);
        if (moved == current) {
            moved += target > current ? 1 : -1;
        }

        return clampInt(moved, min, max);
    }

    private BigDecimal clampDecimal(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value.compareTo(min) < 0) {
            return min;
        }
        if (value.compareTo(max) > 0) {
            return max;
        }
        return value;
    }

    private int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
