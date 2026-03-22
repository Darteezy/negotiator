package org.GLM.negoriator.negotiation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.GLM.negoriator.negotiation.CounterOfferGenerator.CounterProposal;
import org.GLM.negoriator.negotiation.NegotiationEngine.DecisionReason;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationIssue;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.springframework.stereotype.Component;

/**
 * negotitation engine flow gets orchestrated
 */
@Component
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
            List<OfferVector> counterOffers;
            NegotiationIssue focusIssue;

            if (context.strategy() == NegotiationStrategy.MESO) {
            counterOffers = mesoCounterOffers(buyerProfile, context, request.bounds(), supplierOffer);
            focusIssue = counterOfferGenerator.issueToImprove(buyerProfile, context, request.bounds(), supplierOffer);
            } else {
            CounterProposal proposal = counterOfferGenerator.counterProposal(
                buyerProfile,
                context,
                request.bounds(),
                supplierOffer);
            counterOffers = List.of(proposal.offer());
            focusIssue = proposal.issue();
            }

            return new NegotiationResponse(
                    decision,
                    nextState,
                counterOffers,
                    evaluation,
                    request.supplierModel().archetypeBeliefs(),
                decisionOutcome.reasonCode(),
                focusIssue,
                counterExplanation(buyerUtility, targetUtility, focusIssue, context.strategy(), counterOffers.size()));
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
                ? acceptanceExplanation(buyerUtility, targetUtility)
                : rejectionExplanation(buyerUtility, buyerProfile.reservationUtility(), decisionOutcome.reasonCode()));
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

    private String acceptanceExplanation(BigDecimal buyerUtility, BigDecimal targetUtility) {
        return "Accepted because the offer reached buyer utility "
                + buyerUtility
                + ", which meets the current round target of "
                + targetUtility
                + ".";
    }

    private String counterExplanation(
            BigDecimal buyerUtility,
            BigDecimal targetUtility,
            NegotiationIssue issue,
            NegotiationStrategy strategy,
            int counterOfferCount
    ) {
        String prefix = strategy == NegotiationStrategy.MESO
                ? "Countered with multiple equivalent options because the offer is still below the current target. Buyer utility is "
                : "Countered because the offer is still below the current target. Buyer utility is ";
        String suffix = strategy == NegotiationStrategy.MESO && counterOfferCount > 1
                ? ". The leading option focuses on "
                + humanIssueName(issue)
                + ", and the remaining options test nearby trade-offs."
                : ". The counteroffer changes "
                + humanIssueName(issue)
                + " because it is the most important remaining gap for the buyer.";

        return prefix
                + buyerUtility
                + " versus a target of "
                + targetUtility
                + suffix;
    }

    private List<OfferVector> mesoCounterOffers(
            BuyerProfile buyerProfile,
            NegotiationContext context,
            NegotiationBounds bounds,
            OfferVector supplierOffer
    ) {
        List<NegotiationIssue> rankedIssues = counterOfferGenerator.rankedIssues(buyerProfile, context, bounds, supplierOffer);
        List<OfferVector> offers = new ArrayList<>();

        for (NegotiationIssue issue : rankedIssues) {
            OfferVector offer = counterOfferGenerator.counterOfferForIssue(buyerProfile, bounds, supplierOffer, issue);
            if (offers.stream().noneMatch(existing -> sameOffer(existing, offer))) {
                offers.add(offer);
            }

            if (offers.size() == 3) {
                break;
            }
        }

        if (offers.isEmpty()) {
            offers.add(counterOfferGenerator.counterOffer(buyerProfile, context, bounds, supplierOffer));
        }

        return offers;
    }

    private boolean sameOffer(OfferVector left, OfferVector right) {
        return left.price().compareTo(right.price()) == 0
                && left.paymentDays() == right.paymentDays()
                && left.deliveryDays() == right.deliveryDays()
                && left.contractMonths() == right.contractMonths();
    }

    private String rejectionExplanation(
            BigDecimal buyerUtility,
            BigDecimal reservationUtility,
            DecisionReason reasonCode
    ) {
        if (reasonCode == DecisionReason.FINAL_ROUND_BELOW_RESERVATION) {
            return "Rejected because the final round was reached and buyer utility "
                    + buyerUtility
                    + " is still below the buyer minimum of "
                    + reservationUtility
                    + ".";
        }

        return "Rejected because buyer utility "
                + buyerUtility
                + " is below the hard continuation floor for this negotiation.";
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
