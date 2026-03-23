package org.GLM.negoriator.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;

import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.IssueWeights;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.junit.jupiter.api.Test;

class BuyerUtilityCalculatorTest {

	private final BuyerUtilityCalculator calculator = new BuyerUtilityCalculator();

	@Test
	void scoresPriceAgainstConfiguredBuyerSpanNotValidationBounds() {
		BuyerProfile buyerProfile = new BuyerProfile(
			new OfferVector(new BigDecimal("130.00"), 60, 3, 6),
			new OfferVector(new BigDecimal("200.00"), 30, 14, 24),
			new IssueWeights(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
			new BigDecimal("0.10"));
		NegotiationBounds bounds = new NegotiationBounds(
			new BigDecimal("50.00"),
			new BigDecimal("200.00"),
			7,
			120,
			1,
			45,
			1,
			36);
		OfferVector supplierOffer = new OfferVector(new BigDecimal("180.00"), 30, 14, 24);

		BigDecimal utility = calculator.calculate(supplierOffer, buyerProfile, bounds);

		assertEquals(new BigDecimal("0.2857"), utility);
	}
}
