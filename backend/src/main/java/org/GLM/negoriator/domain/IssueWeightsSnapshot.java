package org.GLM.negoriator.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.GLM.negoriator.negotiation.NegotiationEngine.IssueWeights;

@Embeddable
public class IssueWeightsSnapshot {

	@Column(name = "price_weight", nullable = false, precision = 10, scale = 4)
	private BigDecimal price;

	@Column(name = "payment_days_weight", nullable = false, precision = 10, scale = 4)
	private BigDecimal paymentDays;

	@Column(name = "delivery_days_weight", nullable = false, precision = 10, scale = 4)
	private BigDecimal deliveryDays;

	@Column(name = "contract_months_weight", nullable = false, precision = 10, scale = 4)
	private BigDecimal contractMonths;

	protected IssueWeightsSnapshot() {
	}

	public IssueWeightsSnapshot(BigDecimal price, BigDecimal paymentDays, BigDecimal deliveryDays, BigDecimal contractMonths) {
		this.price = price;
		this.paymentDays = paymentDays;
		this.deliveryDays = deliveryDays;
		this.contractMonths = contractMonths;
	}

	public static IssueWeightsSnapshot from(IssueWeights issueWeights) {
		return new IssueWeightsSnapshot(
			issueWeights.price(),
			issueWeights.paymentDays(),
			issueWeights.deliveryDays(),
			issueWeights.contractMonths());
	}

	public IssueWeights toIssueWeights() {
		return new IssueWeights(price, paymentDays, deliveryDays, contractMonths);
	}

	public BigDecimal getPrice() {
		return price;
	}

	public BigDecimal getPaymentDays() {
		return paymentDays;
	}

	public BigDecimal getDeliveryDays() {
		return deliveryDays;
	}

	public BigDecimal getContractMonths() {
		return contractMonths;
	}
}
