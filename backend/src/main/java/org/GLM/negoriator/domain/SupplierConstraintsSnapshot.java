package org.GLM.negoriator.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;

@Embeddable
public class SupplierConstraintsSnapshot {

	@Column(name = "supplier_price_floor", precision = 19, scale = 4)
	private BigDecimal priceFloor;

	@Column(name = "supplier_payment_days_ceiling")
	private Integer paymentDaysCeiling;

	@Column(name = "supplier_delivery_days_floor")
	private Integer deliveryDaysFloor;

	@Column(name = "supplier_contract_months_floor")
	private Integer contractMonthsFloor;

	protected SupplierConstraintsSnapshot() {
	}

	public SupplierConstraintsSnapshot(
		BigDecimal priceFloor,
		Integer paymentDaysCeiling,
		Integer deliveryDaysFloor,
		Integer contractMonthsFloor
	) {
		this.priceFloor = priceFloor;
		this.paymentDaysCeiling = paymentDaysCeiling;
		this.deliveryDaysFloor = deliveryDaysFloor;
		this.contractMonthsFloor = contractMonthsFloor;
	}

	public static SupplierConstraintsSnapshot empty() {
		return new SupplierConstraintsSnapshot(null, null, null, null);
	}

	public SupplierConstraintsSnapshot merge(SupplierConstraintsSnapshot update) {
		if (update == null) {
			return this;
		}

		return new SupplierConstraintsSnapshot(
			update.priceFloor != null ? update.priceFloor : priceFloor,
			update.paymentDaysCeiling != null ? update.paymentDaysCeiling : paymentDaysCeiling,
			update.deliveryDaysFloor != null ? update.deliveryDaysFloor : deliveryDaysFloor,
			update.contractMonthsFloor != null ? update.contractMonthsFloor : contractMonthsFloor);
	}

	public OfferVector clamp(OfferVector offerVector) {
		if (offerVector == null) {
			return null;
		}

		BigDecimal nextPrice = offerVector.price();
		int nextPaymentDays = offerVector.paymentDays();
		int nextDeliveryDays = offerVector.deliveryDays();
		int nextContractMonths = offerVector.contractMonths();

		if (priceFloor != null && nextPrice.compareTo(priceFloor) < 0) {
			nextPrice = priceFloor;
		}

		if (paymentDaysCeiling != null && nextPaymentDays > paymentDaysCeiling) {
			nextPaymentDays = paymentDaysCeiling;
		}

		if (deliveryDaysFloor != null && nextDeliveryDays < deliveryDaysFloor) {
			nextDeliveryDays = deliveryDaysFloor;
		}

		if (contractMonthsFloor != null && nextContractMonths < contractMonthsFloor) {
			nextContractMonths = contractMonthsFloor;
		}

		return new OfferVector(nextPrice, nextPaymentDays, nextDeliveryDays, nextContractMonths);
	}

	public boolean isEmpty() {
		return priceFloor == null
			&& paymentDaysCeiling == null
			&& deliveryDaysFloor == null
			&& contractMonthsFloor == null;
	}

	public BigDecimal getPriceFloor() {
		return priceFloor;
	}

	public Integer getPaymentDaysCeiling() {
		return paymentDaysCeiling;
	}

	public Integer getDeliveryDaysFloor() {
		return deliveryDaysFloor;
	}

	public Integer getContractMonthsFloor() {
		return contractMonthsFloor;
	}
}