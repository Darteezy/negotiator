package org.GLM.negoriator.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.GLM.negoriator.application.NegotiationDefaults;
import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationContext;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationRequest;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationState;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.junit.jupiter.api.Test;

class NegotiationEngineImplTest {

	private final NegotiationEngineImpl negotiationEngine = new NegotiationEngineImpl();
	private final BuyerProfile buyerProfile = NegotiationDefaults.buyerProfile();
	private final NegotiationBounds testBounds = new NegotiationBounds(
		new BigDecimal("80.00"),
		new BigDecimal("120.00"),
		30,
		90,
		3,
		14,
		3,
		24);

	@Test
	void raisesPriceWhenSupplierConcedesOnPaymentDays() {
		OfferVector supplierOffer = new OfferVector(new BigDecimal("115.00"), 50, 10, 18);
		NegotiationContext context = new NegotiationContext(
			2,
			10,
			NegotiationStrategy.BASELINE,
			NegotiationState.COUNTERED,
			new BigDecimal("0.15"),
			List.of(
				new OfferVector(new BigDecimal("115.00"), 30, 10, 18),
				new OfferVector(new BigDecimal("102.50"), 30, 10, 18)));

		var response = negotiationEngine.negotiate(new NegotiationRequest(
			supplierOffer,
			context,
			buyerProfile,
			NegotiationDefaults.supplierModel(),
			testBounds));

		assertEquals(NegotiationEngine.Decision.COUNTER, response.decision());
		assertTrue(response.counterOffers().getFirst().price().compareTo(new BigDecimal("102.50")) > 0);
	}

	@Test
	void keepsPurePriceCounterStableWhenThereIsNoCrossIssueMovement() {
		OfferVector supplierOffer = new OfferVector(new BigDecimal("115.00"), 50, 10, 18);
		NegotiationContext context = new NegotiationContext(
			2,
			10,
			NegotiationStrategy.BASELINE,
			NegotiationState.COUNTERED,
			new BigDecimal("0.15"),
			List.of(
				new OfferVector(new BigDecimal("115.00"), 50, 10, 18),
				new OfferVector(new BigDecimal("102.50"), 50, 10, 18)));

		var response = negotiationEngine.negotiate(new NegotiationRequest(
			supplierOffer,
			context,
			buyerProfile,
			NegotiationDefaults.supplierModel(),
			testBounds));

		assertEquals(NegotiationEngine.Decision.COUNTER, response.decision());
		assertEquals(new BigDecimal("102.50"), response.counterOffers().getFirst().price());
	}
}
