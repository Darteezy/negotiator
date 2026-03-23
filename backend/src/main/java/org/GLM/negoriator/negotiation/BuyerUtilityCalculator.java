package org.GLM.negoriator.negotiation;

import java.math.BigDecimal;

public class BuyerUtilityCalculator {

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

        BigDecimal priceScore = Normalization.normalizeInvertedDecimal(
                offer.price(),
                bounds.minPrice(),
                bounds.maxPrice()
        );
        BigDecimal paymentScore = Normalization.normalizePositiveInt(
                offer.paymentDays(),
                bounds.minPaymentDays(),
                bounds.maxPaymentDays()
        );
        BigDecimal deliveryScore = Normalization.normalizeNegativeInt(
                offer.deliveryDays(),
                bounds.minDeliveryDays(),
                bounds.maxDeliveryDays()
        );

        BigDecimal contractScore = Normalization.normalizeNegativeInt(
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

        return Normalization.clamp(utility);
    }
}
