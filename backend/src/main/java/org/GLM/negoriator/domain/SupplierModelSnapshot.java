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

	@Column(name = "supplier_update_sensitivity", nullable = false, precision = 10, scale = 4)
	private BigDecimal updateSensitivity;

	@Column(name = "supplier_reservation_utility", nullable = false, precision = 10, scale = 4)
	private BigDecimal reservationUtility;

	protected SupplierModelSnapshot() {
	}

	public SupplierModelSnapshot(SupplierBeliefSnapshot beliefs, BigDecimal updateSensitivity, BigDecimal reservationUtility) {
		this.beliefs = beliefs;
		this.updateSensitivity = updateSensitivity;
		this.reservationUtility = reservationUtility;
	}

	public static SupplierModelSnapshot from(SupplierModel supplierModel) {
		return new SupplierModelSnapshot(
			SupplierBeliefSnapshot.from(supplierModel.archetypeBeliefs()),
			supplierModel.updateSensitivity(),
			supplierModel.reservationUtility());
	}

	public SupplierModel toSupplierModel() {
		return new SupplierModel(beliefs.toBeliefMap(), updateSensitivity, reservationUtility);
	}

	public SupplierBeliefSnapshot getBeliefs() {
		return beliefs;
	}

	public BigDecimal getUpdateSensitivity() {
		return updateSensitivity;
	}

	public BigDecimal getReservationUtility() {
		return reservationUtility;
	}
}
