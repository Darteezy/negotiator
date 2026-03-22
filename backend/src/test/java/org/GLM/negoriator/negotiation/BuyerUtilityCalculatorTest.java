package org.GLM.negoriator.negotiation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.IssueWeights;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.junit.jupiter.api.Test;

class BuyerUtilityCalculatorTest {

    private final BuyerUtilityCalculator calculator = new BuyerUtilityCalculator();

    @Test
    void normalizePriceKeepsFractionalPrecision() {
        assertThat(calculator.normalizePrice(new BigDecimal("80"), new BigDecimal("80"), new BigDecimal("120")))
                .isEqualByComparingTo("1.0000");
        assertThat(calculator.normalizePrice(new BigDecimal("85"), new BigDecimal("80"), new BigDecimal("120")))
                .isEqualByComparingTo("0.8750");
        assertThat(calculator.normalizePrice(new BigDecimal("120"), new BigDecimal("80"), new BigDecimal("120")))
                .isEqualByComparingTo("0.0000");
    }

    @Test
    void calculateReturnsExpectedUtilitiesAtExactBounds() {
        assertThat(calculator.calculate(
                new OfferVector(new BigDecimal("80"), 90, 3, 3),
                buyerProfile(),
                bounds()))
                .isEqualByComparingTo("1.0000");

        assertThat(calculator.calculate(
                new OfferVector(new BigDecimal("120"), 30, 14, 24),
                buyerProfile(),
                bounds()))
                .isEqualByComparingTo("0.0000");
    }

    @Test
    void calculateClampsOutOfBoundsOffersIntoZeroToOneRange() {
        assertThat(calculator.calculate(
                new OfferVector(new BigDecimal("70"), 120, 1, 1),
                buyerProfile(),
                bounds()))
                .isEqualByComparingTo("1.0000");

        assertThat(calculator.calculate(
                new OfferVector(new BigDecimal("130"), 0, 30, 36),
                buyerProfile(),
                bounds()))
                .isEqualByComparingTo("0.0000");
    }

    @Test
    void normalizesWeightsBeforeCalculatingUtility() {
        BuyerProfile profile = new BuyerProfile(
                new OfferVector(new BigDecimal("90"), 60, 3, 6),
                new OfferVector(new BigDecimal("120"), 30, 14, 24),
                new IssueWeights(
                        new BigDecimal("4.0"),
                        new BigDecimal("2.0"),
                        new BigDecimal("2.5"),
                        new BigDecimal("1.5")),
                new BigDecimal("0.45"),
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        assertThat(calculator.calculate(
                new OfferVector(new BigDecimal("80"), 90, 3, 3),
                profile,
                bounds()))
                .isEqualByComparingTo("1.0000");
    }

    private BuyerProfile buyerProfile() {
        return new BuyerProfile(
                new OfferVector(new BigDecimal("90"), 60, 3, 6),
                new OfferVector(new BigDecimal("120"), 30, 14, 24),
                new IssueWeights(
                        new BigDecimal("0.40"),
                        new BigDecimal("0.20"),
                        new BigDecimal("0.25"),
                        new BigDecimal("0.15")),
                new BigDecimal("0.45"),
                BigDecimal.ZERO,
                BigDecimal.ZERO);
    }

    private NegotiationBounds bounds() {
        return new NegotiationBounds(new BigDecimal("80"), new BigDecimal("120"), 30, 90, 3, 14, 3, 24);
    }
}
