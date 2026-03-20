package org.GLM.negoriator.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;

@Embeddable
public class NegotiationBoundsSnapshot {

	@Column(name = "min_price", nullable = false, precision = 19, scale = 4)
	private BigDecimal minPrice;

	@Column(name = "max_price", nullable = false, precision = 19, scale = 4)
	private BigDecimal maxPrice;

	@Column(name = "min_payment_days", nullable = false)
	private Integer minPaymentDays;

	@Column(name = "max_payment_days", nullable = false)
	private Integer maxPaymentDays;

	@Column(name = "min_delivery_days", nullable = false)
	private Integer minDeliveryDays;

	@Column(name = "max_delivery_days", nullable = false)
	private Integer maxDeliveryDays;

	@Column(name = "min_contract_months", nullable = false)
	private Integer minContractMonths;

	@Column(name = "max_contract_months", nullable = false)
	private Integer maxContractMonths;

	protected NegotiationBoundsSnapshot() {
	}

	public NegotiationBoundsSnapshot(
		BigDecimal minPrice,
		BigDecimal maxPrice,
		Integer minPaymentDays,
		Integer maxPaymentDays,
		Integer minDeliveryDays,
		Integer maxDeliveryDays,
		Integer minContractMonths,
		Integer maxContractMonths
	) {
		this.minPrice = minPrice;
		this.maxPrice = maxPrice;
		this.minPaymentDays = minPaymentDays;
		this.maxPaymentDays = maxPaymentDays;
		this.minDeliveryDays = minDeliveryDays;
		this.maxDeliveryDays = maxDeliveryDays;
		this.minContractMonths = minContractMonths;
		this.maxContractMonths = maxContractMonths;
	}

	public static NegotiationBoundsSnapshot from(NegotiationBounds bounds) {
		return new NegotiationBoundsSnapshot(
			bounds.minPrice(),
			bounds.maxPrice(),
			bounds.minPaymentDays(),
			bounds.maxPaymentDays(),
			bounds.minDeliveryDays(),
			bounds.maxDeliveryDays(),
			bounds.minContractMonths(),
			bounds.maxContractMonths());
	}

	public NegotiationBounds toNegotiationBounds() {
		return new NegotiationBounds(
			minPrice,
			maxPrice,
			minPaymentDays,
			maxPaymentDays,
			minDeliveryDays,
			maxDeliveryDays,
			minContractMonths,
			maxContractMonths);
	}

	public BigDecimal getMinPrice() {
		return minPrice;
	}

	public BigDecimal getMaxPrice() {
		return maxPrice;
	}

	public Integer getMinPaymentDays() {
		return minPaymentDays;
	}

	public Integer getMaxPaymentDays() {
		return maxPaymentDays;
	}

	public Integer getMinDeliveryDays() {
		return minDeliveryDays;
	}

	public Integer getMaxDeliveryDays() {
		return maxDeliveryDays;
	}

	public Integer getMinContractMonths() {
		return minContractMonths;
	}

	public Integer getMaxContractMonths() {
		return maxContractMonths;
	}
}
