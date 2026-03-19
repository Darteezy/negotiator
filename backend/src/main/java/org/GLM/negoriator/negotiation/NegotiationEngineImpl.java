package org.GLM.negoriator.negotiation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * negotitation engine flow gets orchestrated
 */
public class NegotiationEngineImpl implements NegotiationEngine {

    private static final int SCALE = 4;

    private final BuyerUtilityCalculator utilityCalculator;
    private final DecisionMaker decisionMaker;
    private final CounterOfferGenerator counterOfferGenerator;

    public NegotiationEngineImpl() {
        this(new BuyerUtilityCalculator(), new DecisionMaker(), new CounterOfferGenerator());
    }

    NegotiationEngineImpl(
            BuyerUtilityCalculator utilityCalculator,
            DecisionMaker decisionMaker,
            CounterOfferGenerator counterOfferGenerator
    ) {
        this.utilityCalculator = utilityCalculator;
        this.decisionMaker = decisionMaker;
        this.counterOfferGenerator = counterOfferGenerator;
    }

    @Override
    public NegotiationResponse negotiate(NegotiationRequest request) {
        OfferVector supplierOffer = request.supplierOffer();
        BuyerProfile buyerProfile = request.buyerProfile();
        NegotiationContext context = request.context();

        BigDecimal buyerUtility = utilityCalculator.calculate(supplierOffer, buyerProfile, request.bounds());
        BigDecimal targetUtility = decisionMaker.targetUtility(buyerProfile, context);
        BigDecimal estimatedSupplierUtility = estimateSupplierUtility(supplierOffer, request);
        BigDecimal continuationValue = calculateContinuationValue(targetUtility, context);
        BigDecimal nashProduct = calculateNashProduct(buyerUtility, buyerProfile, estimatedSupplierUtility, request.supplierModel());

        OfferEvaluation evaluation = new OfferEvaluation(
                buyerUtility,
                estimatedSupplierUtility,
                targetUtility,
                continuationValue,
                nashProduct);

        if (violatesBuyerReservation(supplierOffer, buyerProfile.reservationOffer())) {
            return new NegotiationResponse(
                    Decision.REJECT,
                    NegotiationState.REJECTED,
                    List.of(),
                    evaluation,
                    request.supplierModel().archetypeBeliefs(),
                    "Rejected because the offer is outside the buyer reservation limits.");
        }

        Decision decision = decisionMaker.decide(buyerUtility, buyerProfile, context);
        NegotiationState nextState = nextState(decision);

        if (decision == Decision.COUNTER) {
            OfferVector counterOffer = counterOfferGenerator.counterOffer(
                    buyerProfile,
                    context,
                    request.bounds(),
                    supplierOffer);

            String changedIssue = counterOfferGenerator.issueToImprove(
                    buyerProfile,
                    request.bounds(),
                    supplierOffer);

            return new NegotiationResponse(
                    decision,
                    nextState,
                    List.of(counterOffer),
                    evaluation,
                    request.supplierModel().archetypeBeliefs(),
                    "Countered because utility "
                            + buyerUtility
                            + " is below target "
                            + targetUtility
                            + ". Improved "
                            + changedIssue
                            + " in the counteroffer.");
        }

        return new NegotiationResponse(
                decision,
                nextState,
                List.of(),
                evaluation,
                request.supplierModel().archetypeBeliefs(),
                decision == Decision.ACCEPT
                        ? "Accepted because utility " + buyerUtility + " met target " + targetUtility + "."
                        : "Rejected because utility " + buyerUtility + " is too low to continue.");
    }

    private boolean violatesBuyerReservation(OfferVector offer, OfferVector reservationOffer) {
        return offer.price().compareTo(reservationOffer.price()) > 0
                || offer.paymentDays() < reservationOffer.paymentDays()
                || offer.deliveryDays() > reservationOffer.deliveryDays()
                || offer.contractMonths() > reservationOffer.contractMonths();
    }

    private NegotiationState nextState(Decision decision) {
        return switch (decision) {
            case ACCEPT -> NegotiationState.ACCEPTED;
            case COUNTER -> NegotiationState.COUNTERED;
            case REJECT -> NegotiationState.REJECTED;
        };
    }

    private BigDecimal estimateSupplierUtility(OfferVector offer, NegotiationRequest request) {
        Map<SupplierArchetype, BigDecimal> beliefs = request.supplierModel().archetypeBeliefs();

        BigDecimal priceUtility = normalizePositiveDecimal(
                offer.price(),
                request.bounds().minPrice(),
                request.bounds().maxPrice());
        BigDecimal paymentUtility = normalizeNegativeInt(
                offer.paymentDays(),
                request.bounds().minPaymentDays(),
                request.bounds().maxPaymentDays());
        BigDecimal deliveryUtility = normalizePositiveInt(
                offer.deliveryDays(),
                request.bounds().minDeliveryDays(),
                request.bounds().maxDeliveryDays());
        BigDecimal contractUtility = normalizePositiveInt(
                offer.contractMonths(),
                request.bounds().minContractMonths(),
                request.bounds().maxContractMonths());

        BigDecimal weightedUtility = priceUtility.multiply(beliefs.getOrDefault(SupplierArchetype.MARGIN_FOCUSED, BigDecimal.ZERO))
                .add(paymentUtility.multiply(beliefs.getOrDefault(SupplierArchetype.CASHFLOW_FOCUSED, BigDecimal.ZERO)))
                .add(deliveryUtility.multiply(beliefs.getOrDefault(SupplierArchetype.OPERATIONS_FOCUSED, BigDecimal.ZERO)))
                .add(contractUtility.multiply(beliefs.getOrDefault(SupplierArchetype.STABILITY_FOCUSED, BigDecimal.ZERO)));
        BigDecimal totalBeliefWeight = beliefs.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalBeliefWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }

        return weightedUtility.divide(totalBeliefWeight, SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateContinuationValue(BigDecimal targetUtility, NegotiationContext context) {
        BigDecimal walkawayDiscount = BigDecimal.ONE.subtract(context.riskOfWalkaway());
        if (walkawayDiscount.compareTo(BigDecimal.ZERO) < 0) {
            walkawayDiscount = BigDecimal.ZERO;
        }

        return targetUtility.multiply(walkawayDiscount)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateNashProduct(
            BigDecimal buyerUtility,
            BuyerProfile buyerProfile,
            BigDecimal estimatedSupplierUtility,
            SupplierModel supplierModel
    ) {
        BigDecimal buyerGain = buyerUtility.subtract(buyerProfile.reservationUtility()).max(BigDecimal.ZERO);
        BigDecimal supplierGain = estimatedSupplierUtility.subtract(supplierModel.reservationUtility()).max(BigDecimal.ZERO);

        return buyerGain.multiply(supplierGain)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizePositiveDecimal(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (max.compareTo(min) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal normalized = value.subtract(min)
                .divide(max.subtract(min), SCALE, RoundingMode.HALF_UP);

        return clamp(normalized);
    }

    private BigDecimal normalizePositiveInt(int value, int min, int max) {
        if (max == min) {
            return BigDecimal.ZERO;
        }

        BigDecimal normalized = BigDecimal.valueOf(value - min)
                .divide(BigDecimal.valueOf(max - min), SCALE, RoundingMode.HALF_UP);

        return clamp(normalized);
    }

    private BigDecimal normalizeNegativeInt(int value, int min, int max) {
        if (max == min) {
            return BigDecimal.ZERO;
        }

        BigDecimal normalized = BigDecimal.valueOf(max - value)
                .divide(BigDecimal.valueOf(max - min), SCALE, RoundingMode.HALF_UP);

        return clamp(normalized);
    }

    private BigDecimal clamp(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return value;
    }

}
