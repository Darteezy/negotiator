package org.GLM.negoriator.negotiation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import org.GLM.negoriator.negotiation.CounterOfferGenerator.CounterProposal;
import org.GLM.negoriator.negotiation.NegotiationEngine.DecisionReason;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationIssue;
import org.springframework.stereotype.Component;

/**
 * negotitation engine flow gets orchestrated
 */
@Component
public class NegotiationEngineImpl implements NegotiationEngine {

    private static final int SCALE = 4;
    private static final BigDecimal MIN_COUNTER_IMPROVEMENT = new BigDecimal("0.0100");
    private static final BigDecimal SUPPLIER_CONCESSION_RECIPROCITY = new BigDecimal("0.35");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

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
        DecisionMaker.DecisionOutcome decisionOutcome = decisionMaker.evaluate(buyerUtility, buyerProfile, context);
        BigDecimal targetUtility = decisionOutcome.targetUtility();
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
                DecisionReason.OUTSIDE_RESERVATION_LIMITS,
                null,
                reservationLimitExplanation(supplierOffer, buyerProfile.reservationOffer()));
        }

        Decision decision = decisionOutcome.decision();
        NegotiationState nextState = nextState(decision);

        if (decision == Decision.COUNTER) {
            CounterProposal proposal = counterOfferGenerator.counterProposal(
                buyerProfile,
                context,
                request.bounds(),
                supplierOffer);
            List<OfferVector> counterOffers = List.of(tuneCounterOffer(
                proposal.offer(),
                supplierOffer,
                request,
                buyerUtility));
            NegotiationIssue focusIssue = proposal.issue();

            counterOffers = counterOffers.stream()
                .filter(offer -> isViableCounterOffer(offer, supplierOffer, buyerProfile, request.bounds(), buyerUtility))
                .filter(offer -> !offer.matches(supplierOffer))
                .toList();

            if (counterOffers.isEmpty()) {
                OfferVector safeFallback = tuneCounterOffer(
                    counterOfferGenerator.counterOffer(
                    buyerProfile,
                    context,
                    request.bounds(),
                    supplierOffer),
                    supplierOffer,
                    request,
                    buyerUtility);
                if (isViableCounterOffer(safeFallback, supplierOffer, buyerProfile, request.bounds(), buyerUtility)
                    && !safeFallback.matches(supplierOffer)) {
                    counterOffers = List.of(safeFallback);
                }
            }

            return new NegotiationResponse(
                    decision,
                    nextState,
                counterOffers,
                    evaluation,
                    request.supplierModel().archetypeBeliefs(),
                decisionOutcome.reasonCode(),
                focusIssue,
                counterExplanation(buyerUtility, targetUtility, focusIssue));
        }

        return new NegotiationResponse(
                decision,
                nextState,
                List.of(),
                evaluation,
                request.supplierModel().archetypeBeliefs(),
            decisionOutcome.reasonCode(),
            null,
            decision == Decision.ACCEPT
                ? acceptanceExplanation(buyerUtility, targetUtility, decisionOutcome.reasonCode())
                : rejectionExplanation(buyerUtility));
    }

    private boolean isViableCounterOffer(
            OfferVector counterOffer,
            OfferVector supplierOffer,
            BuyerProfile buyerProfile,
            NegotiationBounds bounds,
            BigDecimal supplierUtility
    ) {
        if (violatesBuyerReservation(counterOffer, buyerProfile.reservationOffer())) {
            return false;
        }

        BigDecimal counterUtility = utilityCalculator.calculate(counterOffer, buyerProfile, bounds);
        return counterUtility.compareTo(supplierUtility) > 0;
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

        BigDecimal priceUtility = Normalization.normalizePositiveDecimal(
                offer.price(),
                request.bounds().minPrice(),
                request.bounds().maxPrice());
        BigDecimal paymentUtility = Normalization.normalizeNegativeInt(
                offer.paymentDays(),
                request.bounds().minPaymentDays(),
                request.bounds().maxPaymentDays());
        BigDecimal deliveryUtility = Normalization.normalizePositiveInt(
                offer.deliveryDays(),
                request.bounds().minDeliveryDays(),
                request.bounds().maxDeliveryDays());
        BigDecimal contractUtility = Normalization.normalizePositiveInt(
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

    private String acceptanceExplanation(BigDecimal buyerUtility, BigDecimal targetUtility) {
        return acceptanceExplanation(buyerUtility, targetUtility, DecisionReason.TARGET_UTILITY_MET);
    }

	    private String acceptanceExplanation(
	            BigDecimal buyerUtility,
	            BigDecimal targetUtility,
            DecisionReason reasonCode
    ) {
        if (reasonCode == DecisionReason.FINAL_ROUND_WITHIN_LIMITS) {
            return "Accepted because the final round was reached and the supplier offer stays within the buyer's configured limits.";
        }

	        return "Accepted because the offer reached buyer utility "
	                + buyerUtility
	                + ", which meets the current round target of "
	                + targetUtility
	                + ".";
	    }

    private String counterExplanation(
            BigDecimal buyerUtility,
            BigDecimal targetUtility,
            NegotiationIssue issue
    ) {
	        return "Countered because the offer is still below the current target. Buyer utility is "
	                + buyerUtility
	                + " versus a target of "
	                + targetUtility
	                + ". The counteroffer changes "
	                + humanIssueName(issue)
	                + " because it is the most important remaining gap for the buyer.";
	    }

	    private String rejectionExplanation(BigDecimal buyerUtility) {
	        return "Rejected because the offer only reached buyer utility "
	                + buyerUtility
	                + ", which remains below the buyer's reservation floor.";
	    }

    private OfferVector tuneCounterOffer(
            OfferVector counterOffer,
            OfferVector supplierOffer,
            NegotiationRequest request,
            BigDecimal supplierOfferUtility
    ) {
        OfferVector rebalanced = rebalancePriceForTradeoffs(
            counterOffer,
            supplierOffer,
            request,
            supplierOfferUtility);
        OfferVector ratcheted = applyHistoricalPriceFloor(
            rebalanced,
            supplierOffer,
            request);

        BigDecimal reservationPrice = request.buyerProfile().reservationOffer().price();
        if (ratcheted.price().compareTo(reservationPrice) > 0) {
            return new OfferVector(
                reservationPrice,
                ratcheted.paymentDays(),
                ratcheted.deliveryDays(),
                ratcheted.contractMonths());
        }

        return ratcheted;
    }

    private OfferVector rebalancePriceForTradeoffs(
            OfferVector counterOffer,
            OfferVector supplierOffer,
            NegotiationRequest request,
            BigDecimal supplierOfferUtility
    ) {
        BuyerProfile buyerProfile = request.buyerProfile();
        NegotiationBounds bounds = request.bounds();
        BigDecimal utilityGivebackBudget = recentSupplierConcessionUtility(request.context(), supplierOffer, buyerProfile)
            .multiply(SUPPLIER_CONCESSION_RECIPROCITY)
            .setScale(SCALE, RoundingMode.HALF_UP);

        if (utilityGivebackBudget.compareTo(ZERO) <= 0) {
            return counterOffer;
        }

        BigDecimal counterUtility = utilityCalculator.calculate(counterOffer, buyerProfile, bounds);
        BigDecimal maxUtilityGiveback = counterUtility
            .subtract(supplierOfferUtility)
            .subtract(MIN_COUNTER_IMPROVEMENT);

        if (maxUtilityGiveback.compareTo(ZERO) <= 0) {
            return counterOffer;
        }

        BigDecimal allowedUtilityGiveback = utilityGivebackBudget.min(maxUtilityGiveback);
        BigDecimal priceIncrease = priceIncreaseForUtilityDrop(allowedUtilityGiveback, buyerProfile);
        if (priceIncrease.compareTo(ZERO) <= 0) {
            return counterOffer;
        }

        BigDecimal maxPrice = supplierOffer.price()
            .min(bounds.maxPrice())
            .min(buyerProfile.reservationOffer().price());
        BigDecimal nextPrice = counterOffer.price().add(priceIncrease).min(maxPrice).setScale(2, RoundingMode.HALF_UP);

        if (nextPrice.compareTo(counterOffer.price()) <= 0) {
            return counterOffer;
        }

        return new OfferVector(
            nextPrice,
            counterOffer.paymentDays(),
            counterOffer.deliveryDays(),
            counterOffer.contractMonths());
    }

    private OfferVector applyHistoricalPriceFloor(
            OfferVector counterOffer,
            OfferVector currentSupplierOffer,
            NegotiationRequest request
    ) {
        BigDecimal ratchetFloor = historicalPriceFloor(
            request.context(),
            currentSupplierOffer,
            request.buyerProfile());

        if (ratchetFloor.compareTo(counterOffer.price()) <= 0) {
            return counterOffer;
        }

        BigDecimal nextPrice = ratchetFloor
            .min(currentSupplierOffer.price())
            .min(request.buyerProfile().reservationOffer().price())
            .setScale(2, RoundingMode.HALF_UP);

        if (nextPrice.compareTo(counterOffer.price()) <= 0) {
            return counterOffer;
        }

        return new OfferVector(
            nextPrice,
            counterOffer.paymentDays(),
            counterOffer.deliveryDays(),
            counterOffer.contractMonths());
    }

    private BigDecimal recentSupplierConcessionUtility(
            NegotiationContext context,
            OfferVector currentSupplierOffer,
            BuyerProfile buyerProfile
    ) {
        OfferVector previousSupplierOffer = previousSupplierOffer(context);
        if (previousSupplierOffer == null) {
            return ZERO;
        }

        BigDecimal paymentGain = positiveContributionDelta(
            BuyerPreferenceScoring.paymentScore(previousSupplierOffer, buyerProfile),
            BuyerPreferenceScoring.paymentScore(currentSupplierOffer, buyerProfile),
            buyerProfile.weights().normalized().paymentDays());
        BigDecimal deliveryGain = positiveContributionDelta(
            BuyerPreferenceScoring.deliveryScore(previousSupplierOffer, buyerProfile),
            BuyerPreferenceScoring.deliveryScore(currentSupplierOffer, buyerProfile),
            buyerProfile.weights().normalized().deliveryDays());
        BigDecimal contractGain = positiveContributionDelta(
            BuyerPreferenceScoring.contractScore(previousSupplierOffer, buyerProfile),
            BuyerPreferenceScoring.contractScore(currentSupplierOffer, buyerProfile),
            buyerProfile.weights().normalized().contractMonths());

        return paymentGain.add(deliveryGain).add(contractGain).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal historicalPriceFloor(
            NegotiationContext context,
            OfferVector currentSupplierOffer,
            BuyerProfile buyerProfile
    ) {
        List<OfferVector> history = context.history();
        BigDecimal floor = ZERO;

        for (int index = 0; index + 1 < history.size(); index += 2) {
            OfferVector previousSupplierOffer = history.get(index);
            OfferVector previousBuyerOffer = history.get(index + 1);

            if (!isEquivalentOrBetterNonPricePackage(currentSupplierOffer, previousSupplierOffer)) {
                continue;
            }

            if (previousBuyerOffer.price().compareTo(floor) > 0) {
                floor = previousBuyerOffer.price();
            }
        }

        return floor;
    }

    private OfferVector previousSupplierOffer(NegotiationContext context) {
        List<OfferVector> history = context.history();
        if (history.size() < 2) {
            return null;
        }

        return history.get(history.size() - 2);
    }

    private BigDecimal positiveContributionDelta(
            BigDecimal fromScore,
            BigDecimal toScore,
            BigDecimal weight
    ) {
        BigDecimal delta = toScore.subtract(fromScore);
        if (delta.compareTo(ZERO) <= 0) {
            return ZERO;
        }

        return delta.multiply(weight);
    }

    private boolean isEquivalentOrBetterNonPricePackage(
            OfferVector currentSupplierOffer,
            OfferVector previousSupplierOffer
    ) {
        return currentSupplierOffer.paymentDays() >= previousSupplierOffer.paymentDays()
            && currentSupplierOffer.deliveryDays() <= previousSupplierOffer.deliveryDays()
            && currentSupplierOffer.contractMonths() <= previousSupplierOffer.contractMonths();
    }

    private BigDecimal priceIncreaseForUtilityDrop(
            BigDecimal utilityDrop,
            BuyerProfile buyerProfile
    ) {
        BigDecimal priceWeight = buyerProfile.weights().normalized().price();
        BigDecimal priceSpan = BuyerPreferenceScoring.priceSpan(buyerProfile);
        if (priceWeight.compareTo(ZERO) <= 0 || priceSpan.compareTo(ZERO) <= 0) {
            return ZERO;
        }

        BigDecimal increase = utilityDrop.divide(priceWeight, SCALE + 4, RoundingMode.HALF_UP)
            .multiply(priceSpan)
            .setScale(2, RoundingMode.HALF_UP);

        if (increase.compareTo(ZERO) == 0 && utilityDrop.compareTo(ZERO) > 0) {
            return new BigDecimal("0.01");
        }

        return increase;
    }

    private String reservationLimitExplanation(OfferVector offer, OfferVector reservationOffer) {
        StringBuilder explanation = new StringBuilder("Rejected because the offer breaks buyer reservation limits:");

        if (offer.price().compareTo(reservationOffer.price()) > 0) {
            explanation.append(" price must be at most ").append(reservationOffer.price()).append(";");
        }
        if (offer.paymentDays() < reservationOffer.paymentDays()) {
            explanation.append(" payment days must be at least ").append(reservationOffer.paymentDays()).append(";");
        }
        if (offer.deliveryDays() > reservationOffer.deliveryDays()) {
            explanation.append(" delivery days must be at most ").append(reservationOffer.deliveryDays()).append(";");
        }
        if (offer.contractMonths() > reservationOffer.contractMonths()) {
            explanation.append(" contract months must be at most ").append(reservationOffer.contractMonths()).append(";");
        }

        return explanation.toString();
    }

    private String humanIssueName(NegotiationIssue issue) {
        return switch (issue) {
            case PRICE -> "price";
            case PAYMENT_DAYS -> "payment days";
            case DELIVERY_DAYS -> "delivery days";
            case CONTRACT_MONTHS -> "contract months";
        };
    }

}
