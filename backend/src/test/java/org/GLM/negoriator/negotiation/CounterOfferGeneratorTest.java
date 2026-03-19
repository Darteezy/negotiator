package org.GLM.negoriator.negotiation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.IssueWeights;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationContext;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationState;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.junit.jupiter.api.Test;

class CounterOfferGeneratorTest {

    private final CounterOfferGenerator counterOfferGenerator = new CounterOfferGenerator();

    @Test
    void choosesHigherWeightedGapWhenIssuesAreClose() {
        OfferVector supplierOffer = new OfferVector(new BigDecimal("94"), 60, 5, 6);

        assertThat(counterOfferGenerator.issueToImprove(buyerProfile(), bounds(), supplierOffer))
                .isEqualTo("delivery");
        assertThat(counterOfferGenerator.counterOffer(
                buyerProfile(),
                new NegotiationContext(2, 6, NegotiationState.PENDING, BigDecimal.ZERO, java.util.List.of()),
                bounds(),
                supplierOffer))
                .isEqualTo(new OfferVector(new BigDecimal("94"), 60, 4, 6));
    }

    @Test
    void onlyMovesTheRemainingNonIdealIssue() {
        OfferVector supplierOffer = new OfferVector(new BigDecimal("92"), 60, 3, 6);

        assertThat(counterOfferGenerator.issueToImprove(buyerProfile(), bounds(), supplierOffer))
                .isEqualTo("price");
        assertThat(counterOfferGenerator.counterOffer(
                buyerProfile(),
                new NegotiationContext(2, 6, NegotiationState.PENDING, BigDecimal.ZERO, java.util.List.of()),
                bounds(),
                supplierOffer))
                .isEqualTo(new OfferVector(new BigDecimal("91.00"), 60, 3, 6));
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
