package org.GLM.negoriator.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;

@Embeddable
public class OfferTermsSnapshot {

	@Column(name = "price", nullable = false, precision = 19, scale = 4)
	private BigDecimal price;

	@Column(name = "payment_days", nullable = false)
	private Integer paymentDays;

	@Column(name = "delivery_days", nullable = false)
	private Integer deliveryDays;

	@Column(name = "contract_months", nullable = false)
	private Integer contractMonths;

	protected OfferTermsSnapshot() {
	}

	public OfferTermsSnapshot(BigDecimal price, Integer paymentDays, Integer deliveryDays, Integer contractMonths) {
		this.price = price;
		this.paymentDays = paymentDays;
		this.deliveryDays = deliveryDays;
		this.contractMonths = contractMonths;
	}

	public static OfferTermsSnapshot from(OfferVector offerVector) {
		return new OfferTermsSnapshot(
			offerVector.price(),
			offerVector.paymentDays(),
			offerVector.deliveryDays(),
			offerVector.contractMonths());
	}

	public OfferVector toOfferVector() {
		return new OfferVector(price, paymentDays, deliveryDays, contractMonths);
	}

	public BigDecimal getPrice() {
		return price;
	}

	public Integer getPaymentDays() {
		return paymentDays;
	}

	public Integer getDeliveryDays() {
		return deliveryDays;
	}

	public Integer getContractMonths() {
		return contractMonths;
	}
}
