package org.GLM.negoriator.negotiation;

import java.math.BigDecimal;

public class DecisionMaker {

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
        //TODO
        //1. If utility is high enough - ACCEPT
        //2. If utility is too low - REJECT
        //3. Otherwise -> COUNTER

        return NegotiationEngine.Decision.COUNTER;
    }
}
