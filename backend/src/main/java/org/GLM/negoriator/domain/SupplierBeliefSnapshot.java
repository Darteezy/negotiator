package org.GLM.negoriator.domain;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierArchetype;

@Embeddable
public class SupplierBeliefSnapshot {

	@Column(name = "belief_margin_focused", nullable = false, precision = 10, scale = 4)
	private BigDecimal marginFocused;

	@Column(name = "belief_cashflow_focused", nullable = false, precision = 10, scale = 4)
	private BigDecimal cashflowFocused;

	@Column(name = "belief_operations_focused", nullable = false, precision = 10, scale = 4)
	private BigDecimal operationsFocused;

	@Column(name = "belief_stability_focused", nullable = false, precision = 10, scale = 4)
	private BigDecimal stabilityFocused;

	protected SupplierBeliefSnapshot() {
	}

	public SupplierBeliefSnapshot(
		BigDecimal marginFocused,
		BigDecimal cashflowFocused,
		BigDecimal operationsFocused,
		BigDecimal stabilityFocused
	) {
		this.marginFocused = marginFocused;
		this.cashflowFocused = cashflowFocused;
		this.operationsFocused = operationsFocused;
		this.stabilityFocused = stabilityFocused;
	}

	public static SupplierBeliefSnapshot from(Map<SupplierArchetype, BigDecimal> beliefs) {
		return new SupplierBeliefSnapshot(
			beliefs.getOrDefault(SupplierArchetype.MARGIN_FOCUSED, BigDecimal.ZERO),
			beliefs.getOrDefault(SupplierArchetype.CASHFLOW_FOCUSED, BigDecimal.ZERO),
			beliefs.getOrDefault(SupplierArchetype.OPERATIONS_FOCUSED, BigDecimal.ZERO),
			beliefs.getOrDefault(SupplierArchetype.STABILITY_FOCUSED, BigDecimal.ZERO));
	}

	public Map<SupplierArchetype, BigDecimal> toBeliefMap() {
		Map<SupplierArchetype, BigDecimal> beliefs = new EnumMap<>(SupplierArchetype.class);
		beliefs.put(SupplierArchetype.MARGIN_FOCUSED, marginFocused);
		beliefs.put(SupplierArchetype.CASHFLOW_FOCUSED, cashflowFocused);
		beliefs.put(SupplierArchetype.OPERATIONS_FOCUSED, operationsFocused);
		beliefs.put(SupplierArchetype.STABILITY_FOCUSED, stabilityFocused);
		return beliefs;
	}

	public BigDecimal getMarginFocused() {
		return marginFocused;
	}

	public BigDecimal getCashflowFocused() {
		return cashflowFocused;
	}

	public BigDecimal getOperationsFocused() {
		return operationsFocused;
	}

	public BigDecimal getStabilityFocused() {
		return stabilityFocused;
	}
}
