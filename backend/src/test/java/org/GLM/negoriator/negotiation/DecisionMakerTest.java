package org.GLM.negoriator.negotiation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.IssueWeights;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationContext;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationState;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.junit.jupiter.api.Test;

class DecisionMakerTest {

    private final DecisionMaker decisionMaker = new DecisionMaker();

    @Test
    void targetUtilityConvergesTowardReservationUtilityAcrossRounds() {
        BuyerProfile profile = buyerProfile();

        BigDecimal earlyRound = decisionMaker.targetUtility(
                profile,
                new NegotiationContext(1, 6, NegotiationState.PENDING, BigDecimal.ZERO, java.util.List.of()));
        BigDecimal midRound = decisionMaker.targetUtility(
                profile,
                new NegotiationContext(3, 6, NegotiationState.PENDING, BigDecimal.ZERO, java.util.List.of()));
        BigDecimal finalRound = decisionMaker.targetUtility(
                profile,
                new NegotiationContext(6, 6, NegotiationState.PENDING, BigDecimal.ZERO, java.util.List.of()));
        BigDecimal afterMaxRounds = decisionMaker.targetUtility(
                profile,
                new NegotiationContext(9, 6, NegotiationState.PENDING, BigDecimal.ZERO, java.util.List.of()));

        assertThat(earlyRound).isGreaterThan(midRound);
        assertThat(midRound).isGreaterThan(finalRound);
        assertThat(finalRound).isEqualByComparingTo("0.4500");
        assertThat(afterMaxRounds).isEqualByComparingTo("0.4500");
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
}
