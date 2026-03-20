package org.GLM.negoriator.domain;

import java.math.BigDecimal;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Embeddable;

import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;

@Embeddable
public class BuyerProfileSnapshot {

	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "price", column = @Column(name = "ideal_price", nullable = false, precision = 19, scale = 4)),
		@AttributeOverride(name = "paymentDays", column = @Column(name = "ideal_payment_days", nullable = false)),
		@AttributeOverride(name = "deliveryDays", column = @Column(name = "ideal_delivery_days", nullable = false)),
		@AttributeOverride(name = "contractMonths", column = @Column(name = "ideal_contract_months", nullable = false))
	})
	private OfferTermsSnapshot idealOffer;

	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "price", column = @Column(name = "reservation_price", nullable = false, precision = 19, scale = 4)),
		@AttributeOverride(name = "paymentDays", column = @Column(name = "reservation_payment_days", nullable = false)),
		@AttributeOverride(name = "deliveryDays", column = @Column(name = "reservation_delivery_days", nullable = false)),
		@AttributeOverride(name = "contractMonths", column = @Column(name = "reservation_contract_months", nullable = false))
	})
	private OfferTermsSnapshot reservationOffer;

	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "price", column = @Column(name = "buyer_price_weight", nullable = false, precision = 10, scale = 4)),
		@AttributeOverride(name = "paymentDays", column = @Column(name = "buyer_payment_days_weight", nullable = false, precision = 10, scale = 4)),
		@AttributeOverride(name = "deliveryDays", column = @Column(name = "buyer_delivery_days_weight", nullable = false, precision = 10, scale = 4)),
		@AttributeOverride(name = "contractMonths", column = @Column(name = "buyer_contract_months_weight", nullable = false, precision = 10, scale = 4))
	})
	private IssueWeightsSnapshot weights;

	@Column(name = "buyer_reservation_utility", nullable = false, precision = 10, scale = 4)
	private BigDecimal reservationUtility;

	@Column(name = "buyer_price_penalty_alpha", nullable = false, precision = 10, scale = 4)
	private BigDecimal pricePenaltyAlpha;

	@Column(name = "buyer_price_delivery_interaction_lambda", nullable = false, precision = 10, scale = 4)
	private BigDecimal priceDeliveryInteractionLambda;

	protected BuyerProfileSnapshot() {
	}

	public BuyerProfileSnapshot(
		OfferTermsSnapshot idealOffer,
		OfferTermsSnapshot reservationOffer,
		IssueWeightsSnapshot weights,
		BigDecimal reservationUtility,
		BigDecimal pricePenaltyAlpha,
		BigDecimal priceDeliveryInteractionLambda
	) {
		this.idealOffer = idealOffer;
		this.reservationOffer = reservationOffer;
		this.weights = weights;
		this.reservationUtility = reservationUtility;
		this.pricePenaltyAlpha = pricePenaltyAlpha;
		this.priceDeliveryInteractionLambda = priceDeliveryInteractionLambda;
	}

	public static BuyerProfileSnapshot from(BuyerProfile buyerProfile) {
		return new BuyerProfileSnapshot(
			OfferTermsSnapshot.from(buyerProfile.idealOffer()),
			OfferTermsSnapshot.from(buyerProfile.reservationOffer()),
			IssueWeightsSnapshot.from(buyerProfile.weights()),
			buyerProfile.reservationUtility(),
			buyerProfile.pricePenaltyAlpha(),
			buyerProfile.priceDeliveryInteractionLambda());
	}

	public BuyerProfile toBuyerProfile() {
		return new BuyerProfile(
			idealOffer.toOfferVector(),
			reservationOffer.toOfferVector(),
			weights.toIssueWeights(),
			reservationUtility,
			pricePenaltyAlpha,
			priceDeliveryInteractionLambda);
	}

	public OfferTermsSnapshot getIdealOffer() {
		return idealOffer;
	}

	public OfferTermsSnapshot getReservationOffer() {
		return reservationOffer;
	}

	public IssueWeightsSnapshot getWeights() {
		return weights;
	}

	public BigDecimal getReservationUtility() {
		return reservationUtility;
	}

	public BigDecimal getPricePenaltyAlpha() {
		return pricePenaltyAlpha;
	}

	public BigDecimal getPriceDeliveryInteractionLambda() {
		return priceDeliveryInteractionLambda;
	}
}
