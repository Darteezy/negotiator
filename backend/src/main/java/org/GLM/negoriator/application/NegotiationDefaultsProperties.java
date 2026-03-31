package org.GLM.negoriator.application;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.IssueWeights;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierArchetype;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierModel;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "negotiation.defaults")
public class NegotiationDefaultsProperties {

	private String strategy = NegotiationStrategy.BASELINE.name();
	private int maxRounds = 8;
	private BigDecimal riskOfWalkaway = new BigDecimal("0.15");
	private BuyerProfileProperties buyerProfile = new BuyerProfileProperties();
	private BoundsProperties bounds = new BoundsProperties();
	private SupplierModelProperties supplierModel = new SupplierModelProperties();

	@PostConstruct
	void validate() {
		new SessionConfigurationValidator().validate(toStartSessionCommand());
	}

	NegotiationStrategy defaultStrategy() {
		return NegotiationStrategy.valueOf(strategy.trim().toUpperCase(java.util.Locale.ROOT));
	}

	NegotiationApplicationService.StartSessionCommand toStartSessionCommand() {
		return new NegotiationApplicationService.StartSessionCommand(
			defaultStrategy(),
			maxRounds,
			riskOfWalkaway,
			toBuyerProfile(),
			toBounds(),
			toSupplierModel());
	}

	BuyerProfile toBuyerProfile() {
		return new BuyerProfile(
			buyerProfile.idealOffer.toOfferVector(),
			buyerProfile.reservationOffer.toOfferVector(),
			buyerProfile.weights.toIssueWeights(),
			buyerProfile.reservationUtility);
	}

	NegotiationBounds toBounds() {
		return new NegotiationBounds(
			bounds.minPrice,
			bounds.maxPrice,
			bounds.minPaymentDays,
			bounds.maxPaymentDays,
			bounds.minDeliveryDays,
			bounds.maxDeliveryDays,
			bounds.minContractMonths,
			bounds.maxContractMonths);
	}

	SupplierModel toSupplierModel() {
		return new SupplierModel(
			Map.of(
				SupplierArchetype.MARGIN_FOCUSED, supplierModel.marginFocused,
				SupplierArchetype.CASHFLOW_FOCUSED, supplierModel.cashflowFocused,
				SupplierArchetype.OPERATIONS_FOCUSED, supplierModel.operationsFocused,
				SupplierArchetype.STABILITY_FOCUSED, supplierModel.stabilityFocused),
			supplierModel.reservationUtility);
	}

	public String getStrategy() {
		return strategy;
	}

	public void setStrategy(String strategy) {
		this.strategy = strategy;
	}

	public int getMaxRounds() {
		return maxRounds;
	}

	public void setMaxRounds(int maxRounds) {
		this.maxRounds = maxRounds;
	}

	public BigDecimal getRiskOfWalkaway() {
		return riskOfWalkaway;
	}

	public void setRiskOfWalkaway(BigDecimal riskOfWalkaway) {
		this.riskOfWalkaway = riskOfWalkaway;
	}

	public BuyerProfileProperties getBuyerProfile() {
		return buyerProfile;
	}

	public void setBuyerProfile(BuyerProfileProperties buyerProfile) {
		this.buyerProfile = buyerProfile;
	}

	public BoundsProperties getBounds() {
		return bounds;
	}

	public void setBounds(BoundsProperties bounds) {
		this.bounds = bounds;
	}

	public SupplierModelProperties getSupplierModel() {
		return supplierModel;
	}

	public void setSupplierModel(SupplierModelProperties supplierModel) {
		this.supplierModel = supplierModel;
	}

	public static class BuyerProfileProperties {

		private OfferTermsProperties idealOffer = new OfferTermsProperties(new BigDecimal("90.00"), 60, 7, 6);
		private OfferTermsProperties reservationOffer = new OfferTermsProperties(new BigDecimal("120.00"), 30, 30, 24);
		private IssueWeightsProperties weights = new IssueWeightsProperties(
			new BigDecimal("0.40"),
			new BigDecimal("0.20"),
			new BigDecimal("0.25"),
			new BigDecimal("0.15"));
		private BigDecimal reservationUtility = BigDecimal.ZERO;

		public OfferTermsProperties getIdealOffer() {
			return idealOffer;
		}

		public void setIdealOffer(OfferTermsProperties idealOffer) {
			this.idealOffer = idealOffer;
		}

		public OfferTermsProperties getReservationOffer() {
			return reservationOffer;
		}

		public void setReservationOffer(OfferTermsProperties reservationOffer) {
			this.reservationOffer = reservationOffer;
		}

		public IssueWeightsProperties getWeights() {
			return weights;
		}

		public void setWeights(IssueWeightsProperties weights) {
			this.weights = weights;
		}

		public BigDecimal getReservationUtility() {
			return reservationUtility;
		}

		public void setReservationUtility(BigDecimal reservationUtility) {
			this.reservationUtility = reservationUtility;
		}
	}

	public static class BoundsProperties {

		private BigDecimal minPrice = new BigDecimal("80.00");
		private BigDecimal maxPrice = new BigDecimal("120.00");
		private int minPaymentDays = 30;
		private int maxPaymentDays = 90;
		private int minDeliveryDays = 7;
		private int maxDeliveryDays = 30;
		private int minContractMonths = 3;
		private int maxContractMonths = 24;

		public BigDecimal getMinPrice() {
			return minPrice;
		}

		public void setMinPrice(BigDecimal minPrice) {
			this.minPrice = minPrice;
		}

		public BigDecimal getMaxPrice() {
			return maxPrice;
		}

		public void setMaxPrice(BigDecimal maxPrice) {
			this.maxPrice = maxPrice;
		}

		public int getMinPaymentDays() {
			return minPaymentDays;
		}

		public void setMinPaymentDays(int minPaymentDays) {
			this.minPaymentDays = minPaymentDays;
		}

		public int getMaxPaymentDays() {
			return maxPaymentDays;
		}

		public void setMaxPaymentDays(int maxPaymentDays) {
			this.maxPaymentDays = maxPaymentDays;
		}

		public int getMinDeliveryDays() {
			return minDeliveryDays;
		}

		public void setMinDeliveryDays(int minDeliveryDays) {
			this.minDeliveryDays = minDeliveryDays;
		}

		public int getMaxDeliveryDays() {
			return maxDeliveryDays;
		}

		public void setMaxDeliveryDays(int maxDeliveryDays) {
			this.maxDeliveryDays = maxDeliveryDays;
		}

		public int getMinContractMonths() {
			return minContractMonths;
		}

		public void setMinContractMonths(int minContractMonths) {
			this.minContractMonths = minContractMonths;
		}

		public int getMaxContractMonths() {
			return maxContractMonths;
		}

		public void setMaxContractMonths(int maxContractMonths) {
			this.maxContractMonths = maxContractMonths;
		}
	}

	public static class SupplierModelProperties {

		private BigDecimal marginFocused = new BigDecimal("0.25");
		private BigDecimal cashflowFocused = new BigDecimal("0.25");
		private BigDecimal operationsFocused = new BigDecimal("0.25");
		private BigDecimal stabilityFocused = new BigDecimal("0.25");
		private BigDecimal reservationUtility = new BigDecimal("0.35");

		public BigDecimal getMarginFocused() {
			return marginFocused;
		}

		public void setMarginFocused(BigDecimal marginFocused) {
			this.marginFocused = marginFocused;
		}

		public BigDecimal getCashflowFocused() {
			return cashflowFocused;
		}

		public void setCashflowFocused(BigDecimal cashflowFocused) {
			this.cashflowFocused = cashflowFocused;
		}

		public BigDecimal getOperationsFocused() {
			return operationsFocused;
		}

		public void setOperationsFocused(BigDecimal operationsFocused) {
			this.operationsFocused = operationsFocused;
		}

		public BigDecimal getStabilityFocused() {
			return stabilityFocused;
		}

		public void setStabilityFocused(BigDecimal stabilityFocused) {
			this.stabilityFocused = stabilityFocused;
		}

		public BigDecimal getReservationUtility() {
			return reservationUtility;
		}

		public void setReservationUtility(BigDecimal reservationUtility) {
			this.reservationUtility = reservationUtility;
		}
	}

	public static class OfferTermsProperties {

		private BigDecimal price;
		private int paymentDays;
		private int deliveryDays;
		private int contractMonths;

		public OfferTermsProperties() {
		}

		public OfferTermsProperties(BigDecimal price, int paymentDays, int deliveryDays, int contractMonths) {
			this.price = price;
			this.paymentDays = paymentDays;
			this.deliveryDays = deliveryDays;
			this.contractMonths = contractMonths;
		}

		OfferVector toOfferVector() {
			return new OfferVector(price, paymentDays, deliveryDays, contractMonths);
		}

		public BigDecimal getPrice() {
			return price;
		}

		public void setPrice(BigDecimal price) {
			this.price = price;
		}

		public int getPaymentDays() {
			return paymentDays;
		}

		public void setPaymentDays(int paymentDays) {
			this.paymentDays = paymentDays;
		}

		public int getDeliveryDays() {
			return deliveryDays;
		}

		public void setDeliveryDays(int deliveryDays) {
			this.deliveryDays = deliveryDays;
		}

		public int getContractMonths() {
			return contractMonths;
		}

		public void setContractMonths(int contractMonths) {
			this.contractMonths = contractMonths;
		}
	}

	public static class IssueWeightsProperties {

		private BigDecimal price;
		private BigDecimal paymentDays;
		private BigDecimal deliveryDays;
		private BigDecimal contractMonths;

		public IssueWeightsProperties() {
		}

		public IssueWeightsProperties(
			BigDecimal price,
			BigDecimal paymentDays,
			BigDecimal deliveryDays,
			BigDecimal contractMonths
		) {
			this.price = price;
			this.paymentDays = paymentDays;
			this.deliveryDays = deliveryDays;
			this.contractMonths = contractMonths;
		}

		IssueWeights toIssueWeights() {
			return new IssueWeights(price, paymentDays, deliveryDays, contractMonths);
		}

		public BigDecimal getPrice() {
			return price;
		}

		public void setPrice(BigDecimal price) {
			this.price = price;
		}

		public BigDecimal getPaymentDays() {
			return paymentDays;
		}

		public void setPaymentDays(BigDecimal paymentDays) {
			this.paymentDays = paymentDays;
		}

		public BigDecimal getDeliveryDays() {
			return deliveryDays;
		}

		public void setDeliveryDays(BigDecimal deliveryDays) {
			this.deliveryDays = deliveryDays;
		}

		public BigDecimal getContractMonths() {
			return contractMonths;
		}

		public void setContractMonths(BigDecimal contractMonths) {
			this.contractMonths = contractMonths;
		}
	}
}
