package org.GLM.negoriator.domain;

import java.math.BigDecimal;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Embeddable;

import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierModel;

@Embeddable
public class SupplierModelSnapshot {

	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "marginFocused", column = @Column(name = "initial_belief_margin_focused", nullable = false, precision = 10, scale = 4)),
		@AttributeOverride(name = "cashflowFocused", column = @Column(name = "initial_belief_cashflow_focused", nullable = false, precision = 10, scale = 4)),
		@AttributeOverride(name = "operationsFocused", column = @Column(name = "initial_belief_operations_focused", nullable = false, precision = 10, scale = 4)),
		@AttributeOverride(name = "stabilityFocused", column = @Column(name = "initial_belief_stability_focused", nullable = false, precision = 10, scale = 4))
	})
	private SupplierBeliefSnapshot beliefs;

	@Column(name = "supplier_reservation_utility", nullable = false, precision = 10, scale = 4)
	private BigDecimal reservationUtility;

	protected SupplierModelSnapshot() {
	}

	public SupplierModelSnapshot(SupplierBeliefSnapshot beliefs, BigDecimal reservationUtility) {
		this.beliefs = beliefs;
		this.reservationUtility = reservationUtility;
	}

	public static SupplierModelSnapshot from(SupplierModel supplierModel) {
		return new SupplierModelSnapshot(
			SupplierBeliefSnapshot.from(supplierModel.archetypeBeliefs()),
			supplierModel.reservationUtility());
	}

	public SupplierModel toSupplierModel() {
		return new SupplierModel(beliefs.toBeliefMap(), reservationUtility);
	}

	public SupplierBeliefSnapshot getBeliefs() {
		return beliefs;
	}

	public BigDecimal getReservationUtility() {
		return reservationUtility;
	}
}
