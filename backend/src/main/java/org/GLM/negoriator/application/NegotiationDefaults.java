package org.GLM.negoriator.application;

import java.math.BigDecimal;
import java.util.Map;

import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.IssueWeights;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierArchetype;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierModel;

public final class NegotiationDefaults {

	private NegotiationDefaults() {
	}

	public static int maxRounds() {
		return 8;
	}

	public static BigDecimal riskOfWalkaway() {
		return new BigDecimal("0.15");
	}

	public static BuyerProfile buyerProfile() {
		return new BuyerProfile(
			new OfferVector(new BigDecimal("90.00"), 60, 3, 6),
			new OfferVector(new BigDecimal("120.00"), 30, 14, 24),
			new IssueWeights(
				new BigDecimal("0.40"),
				new BigDecimal("0.20"),
				new BigDecimal("0.25"),
				new BigDecimal("0.15")),
			new BigDecimal("0.45"),
			BigDecimal.ZERO,
			BigDecimal.ZERO);
	}

	public static NegotiationBounds bounds() {
		return new NegotiationBounds(
			new BigDecimal("80.00"),
			new BigDecimal("120.00"),
			30,
			90,
			3,
			14,
			3,
			24);
	}

	public static SupplierModel supplierModel() {
		return new SupplierModel(
			Map.of(
				SupplierArchetype.MARGIN_FOCUSED, new BigDecimal("0.25"),
				SupplierArchetype.CASHFLOW_FOCUSED, new BigDecimal("0.25"),
				SupplierArchetype.OPERATIONS_FOCUSED, new BigDecimal("0.25"),
				SupplierArchetype.STABILITY_FOCUSED, new BigDecimal("0.25")),
			new BigDecimal("0.50"),
			new BigDecimal("0.35"));
	}

	public static NegotiationApplicationService.StartSessionCommand startSessionCommand() {
		return new NegotiationApplicationService.StartSessionCommand(
			maxRounds(),
			riskOfWalkaway(),
			buyerProfile(),
			bounds(),
			supplierModel());
	}
}