package org.GLM.negoriator.application;

import java.math.BigDecimal;
import java.util.Map;

import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.IssueWeights;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierArchetype;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierModel;

public final class NegotiationDefaults {
	private static final NegotiationStrategy DEFAULT_STRATEGY = NegotiationStrategy.BASELINE;
	private static final int DEFAULT_MAX_ROUNDS = 8;
	private static final BigDecimal DEFAULT_RISK_OF_WALKAWAY = new BigDecimal("0.15");

	private static final OfferVector DEFAULT_IDEAL_OFFER = new OfferVector(new BigDecimal("90.00"), 60, 7, 6);
	private static final OfferVector DEFAULT_RESERVATION_OFFER = new OfferVector(new BigDecimal("120.00"), 30, 30, 24);
	private static final IssueWeights DEFAULT_ISSUE_WEIGHTS = new IssueWeights(
		new BigDecimal("0.40"),
		new BigDecimal("0.20"),
		new BigDecimal("0.25"),
		new BigDecimal("0.15"));
	private static final BigDecimal DEFAULT_BUYER_RESERVATION_UTILITY = BigDecimal.ZERO;

	private static final NegotiationBounds DEFAULT_BOUNDS = new NegotiationBounds(
		new BigDecimal("80.00"),
		new BigDecimal("120.00"),
		30,
		90,
		7,
		30,
		3,
		24);

	private static final SupplierModel DEFAULT_SUPPLIER_MODEL = new SupplierModel(
		Map.of(
			SupplierArchetype.MARGIN_FOCUSED, new BigDecimal("0.25"),
			SupplierArchetype.CASHFLOW_FOCUSED, new BigDecimal("0.25"),
			SupplierArchetype.OPERATIONS_FOCUSED, new BigDecimal("0.25"),
			SupplierArchetype.STABILITY_FOCUSED, new BigDecimal("0.25")),
		new BigDecimal("0.35"));


	private NegotiationDefaults() {
	}

	public static NegotiationStrategy defaultStrategy() {
		return DEFAULT_STRATEGY;
	}

	public static int maxRounds() {
		return maxRounds(defaultStrategy());
	}

	public static int maxRounds(NegotiationStrategy strategy) {
		return DEFAULT_MAX_ROUNDS;
	}

	public static BigDecimal riskOfWalkaway() {
		return DEFAULT_RISK_OF_WALKAWAY;
	}

	public static BuyerProfile buyerProfile() {
		return new BuyerProfile(
			cloneOffer(DEFAULT_IDEAL_OFFER),
			cloneOffer(DEFAULT_RESERVATION_OFFER),
			new IssueWeights(
				DEFAULT_ISSUE_WEIGHTS.price(),
				DEFAULT_ISSUE_WEIGHTS.paymentDays(),
				DEFAULT_ISSUE_WEIGHTS.deliveryDays(),
				DEFAULT_ISSUE_WEIGHTS.contractMonths()),
			DEFAULT_BUYER_RESERVATION_UTILITY);
	}

	public static NegotiationBounds bounds() {
		return new NegotiationBounds(
			DEFAULT_BOUNDS.minPrice(),
			DEFAULT_BOUNDS.maxPrice(),
			DEFAULT_BOUNDS.minPaymentDays(),
			DEFAULT_BOUNDS.maxPaymentDays(),
			DEFAULT_BOUNDS.minDeliveryDays(),
			DEFAULT_BOUNDS.maxDeliveryDays(),
			DEFAULT_BOUNDS.minContractMonths(),
			DEFAULT_BOUNDS.maxContractMonths());
	}

	public static SupplierModel supplierModel() {
		return new SupplierModel(
			Map.of(
				SupplierArchetype.MARGIN_FOCUSED, DEFAULT_SUPPLIER_MODEL.archetypeBeliefs().get(SupplierArchetype.MARGIN_FOCUSED),
				SupplierArchetype.CASHFLOW_FOCUSED, DEFAULT_SUPPLIER_MODEL.archetypeBeliefs().get(SupplierArchetype.CASHFLOW_FOCUSED),
				SupplierArchetype.OPERATIONS_FOCUSED, DEFAULT_SUPPLIER_MODEL.archetypeBeliefs().get(SupplierArchetype.OPERATIONS_FOCUSED),
				SupplierArchetype.STABILITY_FOCUSED, DEFAULT_SUPPLIER_MODEL.archetypeBeliefs().get(SupplierArchetype.STABILITY_FOCUSED)),
			DEFAULT_SUPPLIER_MODEL.reservationUtility());
	}

	public static NegotiationApplicationService.StartSessionCommand startSessionCommand() {
		return startSessionCommand(defaultStrategy());
	}

	public static NegotiationApplicationService.StartSessionCommand startSessionCommand(NegotiationStrategy strategy) {
		return new NegotiationApplicationService.StartSessionCommand(
			strategy,
			maxRounds(strategy),
			riskOfWalkaway(),
			buyerProfile(),
			bounds(),
			supplierModel());
	}

	private static OfferVector cloneOffer(OfferVector offer) {
		return new OfferVector(
			offer.price(),
			offer.paymentDays(),
			offer.deliveryDays(),
			offer.contractMonths());
	}
}
