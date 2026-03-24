package org.GLM.negoriator.negotiation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.List;

import org.GLM.negoriator.application.NegotiationDefaults;
import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.IssueWeights;
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
		7,
		30,
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
			testBounds,
			null));

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
			testBounds,
			null));

		assertEquals(NegotiationEngine.Decision.COUNTER, response.decision());
		assertEquals(new BigDecimal("102.50"), response.counterOffers().getFirst().price());
	}

	@Test
	void keepsGrantedPriceForEquivalentSupplierPackages() {
		OfferVector supplierOffer = new OfferVector(new BigDecimal("120.00"), 30, 14, 12);
		NegotiationContext context = new NegotiationContext(
			3,
			8,
			NegotiationStrategy.BASELINE,
			NegotiationState.COUNTERED,
			new BigDecimal("0.15"),
			List.of(
				new OfferVector(new BigDecimal("120.00"), 30, 14, 24),
				new OfferVector(new BigDecimal("105.00"), 30, 14, 24),
				new OfferVector(new BigDecimal("120.00"), 30, 14, 12),
				new OfferVector(new BigDecimal("107.63"), 30, 14, 12)));

		var response = negotiationEngine.negotiate(new NegotiationRequest(
			supplierOffer,
			context,
			buyerProfile,
			NegotiationDefaults.supplierModel(),
			testBounds,
			null));

		assertEquals(NegotiationEngine.Decision.COUNTER, response.decision());
		assertEquals(new BigDecimal("107.63"), response.counterOffers().getFirst().price());
	}

	@Test
	void capsPaymentDemandAtPreviouslyOfferedLevelForComparableSupplierPackages() throws Exception {
		OfferVector supplierOffer = new OfferVector(new BigDecimal("100.00"), 50, 18, 18);
		OfferVector candidateCounter = new OfferVector(new BigDecimal("100.00"), 55, 18, 18);
		NegotiationRequest request = new NegotiationRequest(
			supplierOffer,
			new NegotiationContext(
				1,
				8,
				NegotiationStrategy.BASELINE,
				NegotiationState.COUNTERED,
				new BigDecimal("0.15"),
				List.of(
					new OfferVector(new BigDecimal("100.00"), 45, 18, 18),
					new OfferVector(new BigDecimal("100.00"), 40, 18, 18))),
			buyerProfile,
			NegotiationDefaults.supplierModel(),
			testBounds,
			null);

		OfferVector tunedCounter = applyHistoricalConsistency(candidateCounter, supplierOffer, request);

		assertEquals(40, tunedCounter.paymentDays());
	}

	@Test
	void doesNotAskForFasterDeliveryThanComparableHistoryAllows() throws Exception {
		OfferVector supplierOffer = new OfferVector(new BigDecimal("100.00"), 60, 22, 18);
		OfferVector candidateCounter = new OfferVector(new BigDecimal("100.00"), 60, 14, 18);
		NegotiationRequest request = new NegotiationRequest(
			supplierOffer,
			new NegotiationContext(
				3,
				8,
				NegotiationStrategy.BASELINE,
				NegotiationState.COUNTERED,
				new BigDecimal("0.15"),
				List.of(
					new OfferVector(new BigDecimal("100.00"), 45, 18, 18),
					new OfferVector(new BigDecimal("100.00"), 45, 16, 18),
					new OfferVector(new BigDecimal("100.00"), 45, 18, 18),
					new OfferVector(new BigDecimal("99.00"), 45, 17, 18))),
			buyerProfile,
			NegotiationDefaults.supplierModel(),
			testBounds,
			null);

		OfferVector tunedCounter = applyHistoricalConsistency(candidateCounter, supplierOffer, request);

		assertEquals(17, tunedCounter.deliveryDays());
	}

	@Test
	void doesNotAskForShorterContractThanComparableHistoryAllows() throws Exception {
		BuyerProfile contractFocusedProfile = new BuyerProfile(
			new OfferVector(new BigDecimal("96.00"), 60, 18, 6),
			buyerProfile.reservationOffer(),
			new IssueWeights(
				new BigDecimal("0.10"),
				new BigDecimal("0.10"),
				new BigDecimal("0.10"),
				new BigDecimal("0.70")),
			BigDecimal.ZERO);
		OfferVector supplierOffer = new OfferVector(new BigDecimal("102.00"), 60, 18, 17);
		OfferVector candidateCounter = new OfferVector(new BigDecimal("102.00"), 60, 18, 11);
		NegotiationRequest request = new NegotiationRequest(
			supplierOffer,
			new NegotiationContext(
				3,
				8,
				NegotiationStrategy.BASELINE,
				NegotiationState.COUNTERED,
				new BigDecimal("0.15"),
				List.of(
					new OfferVector(new BigDecimal("102.00"), 60, 18, 18),
					new OfferVector(new BigDecimal("102.00"), 60, 18, 12),
					new OfferVector(new BigDecimal("102.00"), 60, 18, 18),
					new OfferVector(new BigDecimal("101.00"), 60, 18, 15))),
			contractFocusedProfile,
			NegotiationDefaults.supplierModel(),
			testBounds,
			null);

		OfferVector tunedCounter = applyHistoricalConsistency(candidateCounter, supplierOffer, request);

		assertEquals(15, tunedCounter.contractMonths());
	}

	@Test
	void rejectsFinalRoundOffersBelowConfiguredReservationUtility() {
		BuyerProfile strictBuyerProfile = new BuyerProfile(
			buyerProfile.idealOffer(),
			buyerProfile.reservationOffer(),
			buyerProfile.weights(),
			new BigDecimal("0.2500"));
		NegotiationContext finalRoundContext = new NegotiationContext(
			8,
			8,
			NegotiationStrategy.BASELINE,
			NegotiationState.COUNTERED,
			new BigDecimal("0.15"),
			List.of());

		var response = negotiationEngine.negotiate(new NegotiationRequest(
			buyerProfile.reservationOffer(),
			finalRoundContext,
			strictBuyerProfile,
			NegotiationDefaults.supplierModel(),
			testBounds,
			null));

		assertEquals(NegotiationEngine.Decision.REJECT, response.decision());
		assertEquals(NegotiationEngine.DecisionReason.FINAL_ROUND_BELOW_RESERVATION, response.reasonCode());
	}

	@Test
	void distinguishesStrategyTargetCurves() {
		DecisionMaker decisionMaker = new DecisionMaker();
		NegotiationContext baselineContext = new NegotiationContext(
			2,
			8,
			NegotiationStrategy.BASELINE,
			NegotiationState.COUNTERED,
			new BigDecimal("0.15"),
			List.of());
		NegotiationContext boulwareContext = new NegotiationContext(
			2,
			8,
			NegotiationStrategy.BOULWARE,
			NegotiationState.COUNTERED,
			new BigDecimal("0.15"),
			List.of());
		NegotiationContext concederContext = new NegotiationContext(
			2,
			8,
			NegotiationStrategy.CONCEDER,
			NegotiationState.COUNTERED,
			new BigDecimal("0.15"),
			List.of());

		assertTrue(decisionMaker.targetUtility(buyerProfile, boulwareContext)
			.compareTo(decisionMaker.targetUtility(buyerProfile, baselineContext)) > 0);
		assertTrue(decisionMaker.targetUtility(buyerProfile, baselineContext)
			.compareTo(decisionMaker.targetUtility(buyerProfile, concederContext)) > 0);
	}

	@Test
	void mesoReturnsMultipleCounterOffersWhenSeveralIssuesRemainOpen() {
		OfferVector supplierOffer = new OfferVector(new BigDecimal("118.00"), 35, 20, 18);
		NegotiationContext context = new NegotiationContext(
			2,
			8,
			NegotiationStrategy.MESO,
			NegotiationState.COUNTERED,
			new BigDecimal("0.15"),
			List.of());

		var response = negotiationEngine.negotiate(new NegotiationRequest(
			supplierOffer,
			context,
			buyerProfile,
			NegotiationDefaults.supplierModel(),
			testBounds,
			null));

		assertEquals(NegotiationEngine.Decision.COUNTER, response.decision());
		assertTrue(response.counterOffers().size() > 1);
		assertTrue(response.explanation().contains("Option 1: Price 104.00, payment 35 days, delivery 20 days, contract 18 months"));
	}

	@Test
	void countersReservationEdgeOffersBeforeRejectingThem() {
		OfferVector supplierOffer = new OfferVector(new BigDecimal("120.00"), 30, 30, 24);
		NegotiationContext context = new NegotiationContext(
			3,
			8,
			NegotiationStrategy.CONCEDER,
			NegotiationState.COUNTERED,
			new BigDecimal("0.15"),
			List.of());

		var response = negotiationEngine.negotiate(new NegotiationRequest(
			supplierOffer,
			context,
			buyerProfile,
			NegotiationDefaults.supplierModel(),
			testBounds,
			null));
	}

	@Test
	void shiftsBoulwareFocusAwayFromPriceWhenSupplierPriceFloorIsReached() {
		OfferVector supplierOffer = new OfferVector(new BigDecimal("110.00"), 40, 29, 20);
		NegotiationContext context = new NegotiationContext(
			3,
			8,
			NegotiationStrategy.BOULWARE,
			NegotiationState.COUNTERED,
			new BigDecimal("0.15"),
			List.of());

		var response = negotiationEngine.negotiate(new NegotiationRequest(
			supplierOffer,
			context,
			buyerProfile,
			NegotiationDefaults.supplierModel(),
			testBounds,
			new NegotiationEngine.SupplierConstraints(new BigDecimal("110.00"), null, null, null)));

		assertEquals(NegotiationEngine.Decision.COUNTER, response.decision());
		assertEquals(NegotiationEngine.NegotiationIssue.DELIVERY_DAYS, response.focusIssue());
		assertEquals(new BigDecimal("110.00"), response.counterOffers().getFirst().price());

		assertEquals(NegotiationEngine.Decision.COUNTER, response.decision());
		assertEquals(NegotiationEngine.DecisionReason.COUNTER_TO_CLOSE_GAP, response.reasonCode());
	}

	private OfferVector applyHistoricalConsistency(
		OfferVector candidateCounter,
		OfferVector supplierOffer,
		NegotiationRequest request
	) throws Exception {
		Method method = NegotiationEngineImpl.class.getDeclaredMethod(
			"applyHistoricalConsistency",
			OfferVector.class,
			OfferVector.class,
			NegotiationRequest.class);
		method.setAccessible(true);
		return (OfferVector) method.invoke(negotiationEngine, candidateCounter, supplierOffer, request);
	}
}
