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

	private static volatile DefaultsSnapshot snapshot = DefaultsSnapshot.from(new NegotiationDefaultsProperties());

	private NegotiationDefaults() {
	}

	static void configure(NegotiationDefaultsProperties properties) {
		snapshot = DefaultsSnapshot.from(properties);
	}

	public static NegotiationStrategy defaultStrategy() {
		return snapshot.defaultStrategy();
	}

	public static int maxRounds() {
		return maxRounds(defaultStrategy());
	}

	public static int maxRounds(NegotiationStrategy strategy) {
		return snapshot.maxRounds();
	}

	public static BigDecimal riskOfWalkaway() {
		return snapshot.riskOfWalkaway();
	}

	public static BuyerProfile buyerProfile() {
		return snapshot.buyerProfile();
	}

	public static NegotiationBounds bounds() {
		return snapshot.bounds();
	}

	public static SupplierModel supplierModel() {
		return snapshot.supplierModel();
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

	private record DefaultsSnapshot(
		NegotiationStrategy defaultStrategy,
		int maxRounds,
		BigDecimal riskOfWalkaway,
		BuyerProfile buyerProfile,
		NegotiationBounds bounds,
		SupplierModel supplierModel
	) {
		private static DefaultsSnapshot from(NegotiationDefaultsProperties properties) {
			BuyerProfile buyerProfile = properties.toBuyerProfile();
			NegotiationBounds bounds = properties.toBounds();
			SupplierModel supplierModel = properties.toSupplierModel();

			return new DefaultsSnapshot(
				properties.defaultStrategy(),
				properties.getMaxRounds(),
				properties.getRiskOfWalkaway(),
				new BuyerProfile(
					cloneOffer(buyerProfile.idealOffer()),
					cloneOffer(buyerProfile.reservationOffer()),
					new IssueWeights(
						buyerProfile.weights().price(),
						buyerProfile.weights().paymentDays(),
						buyerProfile.weights().deliveryDays(),
						buyerProfile.weights().contractMonths()),
					buyerProfile.reservationUtility()),
				new NegotiationBounds(
					bounds.minPrice(),
					bounds.maxPrice(),
					bounds.minPaymentDays(),
					bounds.maxPaymentDays(),
					bounds.minDeliveryDays(),
					bounds.maxDeliveryDays(),
					bounds.minContractMonths(),
					bounds.maxContractMonths()),
				new SupplierModel(
					Map.of(
						SupplierArchetype.MARGIN_FOCUSED, supplierModel.archetypeBeliefs().get(SupplierArchetype.MARGIN_FOCUSED),
						SupplierArchetype.CASHFLOW_FOCUSED, supplierModel.archetypeBeliefs().get(SupplierArchetype.CASHFLOW_FOCUSED),
						SupplierArchetype.OPERATIONS_FOCUSED, supplierModel.archetypeBeliefs().get(SupplierArchetype.OPERATIONS_FOCUSED),
						SupplierArchetype.STABILITY_FOCUSED, supplierModel.archetypeBeliefs().get(SupplierArchetype.STABILITY_FOCUSED)),
					supplierModel.reservationUtility()));
		}

		private static OfferVector cloneOffer(OfferVector offer) {
			return new OfferVector(
				offer.price(),
				offer.paymentDays(),
				offer.deliveryDays(),
				offer.contractMonths());
		}
	}
}
