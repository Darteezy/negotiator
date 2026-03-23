package org.GLM.negoriator.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;

import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.IssueWeights;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationContext;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationIssue;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationState;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.junit.jupiter.api.Test;

class CounterOfferGeneratorTest {

	private final CounterOfferGenerator generator = new CounterOfferGenerator();

	@Test
	void ranksPriceGapUsingBuyerPreferenceSpanInsteadOfGlobalBounds() {
		BuyerProfile buyerProfile = new BuyerProfile(
			new OfferVector(new BigDecimal("130.00"), 60, 3, 6),
			new OfferVector(new BigDecimal("140.00"), 30, 14, 24),
			new IssueWeights(
				new BigDecimal("0.40"),
				new BigDecimal("0.20"),
				new BigDecimal("0.25"),
				new BigDecimal("0.15")),
			new BigDecimal("0.45"));
		NegotiationBounds bounds = new NegotiationBounds(
			new BigDecimal("50.00"),
			new BigDecimal("200.00"),
			7,
			120,
			1,
			45,
			1,
			36);
		NegotiationContext context = new NegotiationContext(
			1,
			8,
			NegotiationStrategy.BASELINE,
			NegotiationState.PENDING,
			new BigDecimal("0.15"),
			List.of());
		OfferVector supplierOffer = new OfferVector(new BigDecimal("138.00"), 50, 10, 18);

		NegotiationIssue issue = generator.issueToImprove(buyerProfile, context, bounds, supplierOffer);

		assertEquals(NegotiationIssue.PRICE, issue);
	}
}
