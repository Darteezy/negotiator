package org.GLM.negoriator.negotiation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationIssue;

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
        return counterProposal(profile, context, bounds, supplierOffer).offer();
    }

    CounterProposal counterProposal(
            NegotiationEngine.BuyerProfile profile,
            NegotiationEngine.NegotiationContext context,
            NegotiationEngine.NegotiationBounds bounds,
            NegotiationEngine.OfferVector supplierOffer) {
        NegotiationIssue issueToImprove = issueToImprove(profile, context, bounds, supplierOffer);
        return new CounterProposal(issueToImprove, counterOfferForIssue(profile, bounds, supplierOffer, issueToImprove));
        }

        NegotiationEngine.OfferVector counterOfferForIssue(
            NegotiationEngine.BuyerProfile profile,
            NegotiationEngine.NegotiationBounds bounds,
            NegotiationEngine.OfferVector supplierOffer,
            NegotiationIssue issueToImprove
        ) {
        NegotiationEngine.OfferVector idealOffer = profile.idealOffer();

        BigDecimal price = supplierOffer.price();
        int paymentDays = supplierOffer.paymentDays();
        int deliveryDays = supplierOffer.deliveryDays();
        int contractMonths = supplierOffer.contractMonths();

        switch (issueToImprove) {
            case PAYMENT_DAYS -> paymentDays = moveIntTowards(
                    supplierOffer.paymentDays(),
                    idealOffer.paymentDays(),
                    bounds.minPaymentDays(),
                    bounds.maxPaymentDays());
            case DELIVERY_DAYS -> deliveryDays = moveIntTowards(
                    supplierOffer.deliveryDays(),
                    idealOffer.deliveryDays(),
                    bounds.minDeliveryDays(),
                    bounds.maxDeliveryDays());
            case CONTRACT_MONTHS -> contractMonths = moveIntTowards(
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

    NegotiationIssue issueToImprove(
            NegotiationEngine.BuyerProfile profile,
            NegotiationEngine.NegotiationContext context,
            NegotiationEngine.NegotiationBounds bounds,
            NegotiationEngine.OfferVector supplierOffer
    ) {
            return rankedIssues(profile, context, bounds, supplierOffer).getFirst();
            }

            List<NegotiationIssue> rankedIssues(
                NegotiationEngine.BuyerProfile profile,
                NegotiationEngine.NegotiationContext context,
                NegotiationEngine.NegotiationBounds bounds,
                NegotiationEngine.OfferVector supplierOffer
            ) {
        NegotiationEngine.OfferVector ideal = profile.idealOffer();
        NegotiationEngine.IssueWeights weights = profile.weights().normalized();

        Map<NegotiationIssue, BigDecimal> gapByIssue = new LinkedHashMap<>();
        gapByIssue.put(
                NegotiationIssue.PRICE,
                weightedGap(
                        positiveGap(supplierOffer.price(), ideal.price()),
                        BuyerPreferenceScoring.priceSpan(profile),
                        weights.price()));
        gapByIssue.put(
                NegotiationIssue.PAYMENT_DAYS,
                weightedGap(
                        BigDecimal.valueOf(Math.max(0, ideal.paymentDays() - supplierOffer.paymentDays())),
                        BuyerPreferenceScoring.paymentSpan(profile),
                        weights.paymentDays()));
        gapByIssue.put(
                NegotiationIssue.DELIVERY_DAYS,
                weightedGap(
                        BigDecimal.valueOf(Math.max(0, supplierOffer.deliveryDays() - ideal.deliveryDays())),
                        BuyerPreferenceScoring.deliverySpan(profile),
                        weights.deliveryDays()));
        gapByIssue.put(
                NegotiationIssue.CONTRACT_MONTHS,
                weightedGap(
                        BigDecimal.valueOf(Math.max(0, supplierOffer.contractMonths() - ideal.contractMonths())),
                        BuyerPreferenceScoring.contractSpan(profile),
                        weights.contractMonths()));

        List<Map.Entry<NegotiationIssue, BigDecimal>> rankedEntries = gapByIssue.entrySet().stream()
                .sorted((left, right) -> right.getValue().compareTo(left.getValue()))
                .toList();

        List<NegotiationIssue> rankedIssues = rankedEntries.stream()
                .map(Map.Entry::getKey)
                .toList();
        Map.Entry<NegotiationIssue, BigDecimal> bestCandidate = rankedEntries.getFirst();
        NegotiationIssue previousIssue = previousBuyerIssue(context);

        if (previousIssue != null
                && previousIssue == bestCandidate.getKey()
                && supplierIgnoredPreviousBuyerCounter(context, supplierOffer, previousIssue)) {
            for (Map.Entry<NegotiationIssue, BigDecimal> alternative : rankedEntries) {
                if (alternative.getKey() == bestCandidate.getKey()) {
                    continue;
                }

                if (alternative.getValue().compareTo(BigDecimal.ZERO) > 0
                        && alternative.getValue().compareTo(bestCandidate.getValue().multiply(new BigDecimal("0.70"))) >= 0) {
                    return rankAlternativeFirst(rankedIssues, alternative.getKey());
                }
            }
        }

        return rankedIssues;
    }

    private List<NegotiationIssue> rankAlternativeFirst(
            List<NegotiationIssue> rankedIssues,
            NegotiationIssue alternative
    ) {
        return rankedIssues.stream()
                .sorted((left, right) -> {
                    if (left == alternative) {
                        return -1;
                    }
                    if (right == alternative) {
                        return 1;
                    }
                    return Integer.compare(rankedIssues.indexOf(left), rankedIssues.indexOf(right));
                })
                .toList();
    }

    private NegotiationIssue previousBuyerIssue(NegotiationEngine.NegotiationContext context) {
        List<NegotiationEngine.OfferVector> history = context.history();
        if (history.size() < 2) {
            return null;
        }

        NegotiationEngine.OfferVector previousSupplierOffer = history.get(history.size() - 2);
        NegotiationEngine.OfferVector previousBuyerOffer = history.getLast();

        if (previousBuyerOffer.price().compareTo(previousSupplierOffer.price()) != 0) {
            return NegotiationIssue.PRICE;
        }
        if (previousBuyerOffer.paymentDays() != previousSupplierOffer.paymentDays()) {
            return NegotiationIssue.PAYMENT_DAYS;
        }
        if (previousBuyerOffer.deliveryDays() != previousSupplierOffer.deliveryDays()) {
            return NegotiationIssue.DELIVERY_DAYS;
        }
        if (previousBuyerOffer.contractMonths() != previousSupplierOffer.contractMonths()) {
            return NegotiationIssue.CONTRACT_MONTHS;
        }

        return null;
    }

    private boolean supplierIgnoredPreviousBuyerCounter(
            NegotiationEngine.NegotiationContext context,
            NegotiationEngine.OfferVector currentSupplierOffer,
            NegotiationIssue issue
    ) {
        List<NegotiationEngine.OfferVector> history = context.history();
        if (history.isEmpty()) {
            return false;
        }

        NegotiationEngine.OfferVector previousBuyerOffer = history.getLast();

        return switch (issue) {
            case PRICE -> currentSupplierOffer.price().compareTo(previousBuyerOffer.price()) == 0;
            case PAYMENT_DAYS -> currentSupplierOffer.paymentDays() == previousBuyerOffer.paymentDays();
            case DELIVERY_DAYS -> currentSupplierOffer.deliveryDays() == previousBuyerOffer.deliveryDays();
            case CONTRACT_MONTHS -> currentSupplierOffer.contractMonths() == previousBuyerOffer.contractMonths();
        };
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

    record CounterProposal(NegotiationIssue issue, NegotiationEngine.OfferVector offer) {
    }
}
