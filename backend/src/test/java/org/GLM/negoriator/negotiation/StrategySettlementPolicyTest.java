package org.GLM.negoriator.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.GLM.negoriator.application.NegotiationDefaults;
import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationContext;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationRequest;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationState;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.junit.jupiter.api.Test;

class StrategySettlementPolicyTest {

	private final BuyerUtilityCalculator utilityCalculator = new BuyerUtilityCalculator();
	private final StrategySettlementPolicy settlementPolicy = new StrategySettlementPolicy(utilityCalculator);
	private final BuyerProfile buyerProfile = NegotiationDefaults.buyerProfile();
	private final NegotiationBounds bounds = NegotiationDefaults.bounds();

	@Test
	void openingThresholdsFollowExpectedStrategyOrdering() {
		OfferVector supplierOffer = new OfferVector(new BigDecimal("120.00"), 30, 30, 24);

		StrategySettlementPolicy.SettlementThresholds boulware = thresholds(
			NegotiationStrategy.BOULWARE,
			supplierOffer,
			List.of());
		StrategySettlementPolicy.SettlementThresholds baseline = thresholds(
			NegotiationStrategy.BASELINE,
			supplierOffer,
			List.of());
		StrategySettlementPolicy.SettlementThresholds meso = thresholds(
			NegotiationStrategy.MESO,
			supplierOffer,
			List.of());
		StrategySettlementPolicy.SettlementThresholds conceder = thresholds(
			NegotiationStrategy.CONCEDER,
			supplierOffer,
			List.of());

		assertTrue(boulware.maximumPrice().compareTo(baseline.maximumPrice()) < 0);
		assertTrue(baseline.maximumPrice().compareTo(meso.maximumPrice()) < 0);
		assertTrue(meso.maximumPrice().compareTo(conceder.maximumPrice()) < 0);

		assertTrue(boulware.minimumUtility().compareTo(baseline.minimumUtility()) > 0);
		assertTrue(baseline.minimumUtility().compareTo(meso.minimumUtility()) > 0);
		assertTrue(meso.minimumUtility().compareTo(conceder.minimumUtility()) > 0);
	}

	@Test
	void titForTatRelaxesWhenSupplierMakesMeaningfulNonPriceConcessions() {
		OfferVector anchoredOffer = new OfferVector(new BigDecimal("120.00"), 30, 30, 24);
		OfferVector improvedOffer = new OfferVector(new BigDecimal("120.00"), 45, 14, 12);

		StrategySettlementPolicy.SettlementThresholds stalled = thresholds(
			NegotiationStrategy.TIT_FOR_TAT,
			anchoredOffer,
			List.of(
				anchoredOffer,
				new OfferVector(new BigDecimal("110.00"), 40, 18, 18)));
		StrategySettlementPolicy.SettlementThresholds improved = thresholds(
			NegotiationStrategy.TIT_FOR_TAT,
			improvedOffer,
			List.of(
				anchoredOffer,
				new OfferVector(new BigDecimal("110.00"), 40, 18, 18)));

		assertTrue(improved.maximumPrice().compareTo(stalled.maximumPrice()) > 0);
		assertTrue(improved.minimumUtility().compareTo(stalled.minimumUtility()) < 0);
	}

	@Test
	void clampCounterOfferCapsPriceAtStrategyCeiling() {
		OfferVector supplierOffer = new OfferVector(new BigDecimal("120.00"), 30, 30, 24);
		StrategySettlementPolicy.SettlementThresholds thresholds = thresholds(
			NegotiationStrategy.BASELINE,
			supplierOffer,
			List.of());

		OfferVector counterOffer = new OfferVector(new BigDecimal("119.50"), 55, 10, 12);
		OfferVector clamped = settlementPolicy.clampCounterOffer(counterOffer, thresholds);

		assertEquals(thresholds.maximumPrice(), clamped.price());
		assertEquals(counterOffer.paymentDays(), clamped.paymentDays());
		assertEquals(counterOffer.deliveryDays(), clamped.deliveryDays());
		assertEquals(counterOffer.contractMonths(), clamped.contractMonths());
	}

	@Test
	void reservationUtilityOverridesStrategyFloorWhenItIsStricter() {
		BuyerProfile strictBuyerProfile = new BuyerProfile(
			buyerProfile.idealOffer(),
			buyerProfile.reservationOffer(),
			buyerProfile.weights(),
			new BigDecimal("0.7500"));
		OfferVector supplierOffer = new OfferVector(new BigDecimal("120.00"), 30, 30, 24);

		NegotiationRequest request = new NegotiationRequest(
			supplierOffer,
			new NegotiationContext(
				1,
				NegotiationDefaults.maxRounds(),
				NegotiationStrategy.CONCEDER,
				NegotiationState.PENDING,
				NegotiationDefaults.riskOfWalkaway(),
				List.of()),
			strictBuyerProfile,
			NegotiationDefaults.supplierModel(),
			bounds,
			null);

		StrategySettlementPolicy.SettlementThresholds thresholds = settlementPolicy.thresholds(request);

		assertEquals(new BigDecimal("0.7500"), thresholds.minimumUtility());
	}

	private StrategySettlementPolicy.SettlementThresholds thresholds(
		NegotiationStrategy strategy,
		OfferVector supplierOffer,
		List<OfferVector> history
	) {
		NegotiationRequest request = new NegotiationRequest(
			supplierOffer,
			new NegotiationContext(
				3,
				NegotiationDefaults.maxRounds(),
				strategy,
				NegotiationState.COUNTERED,
				NegotiationDefaults.riskOfWalkaway(),
				history),
			buyerProfile,
			NegotiationDefaults.supplierModel(),
			bounds,
			null);

		return settlementPolicy.thresholds(request);
	}
}
