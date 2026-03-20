package org.GLM.negoriator.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import org.GLM.negoriator.negotiation.NegotiationEngine.OfferEvaluation;

@Embeddable
public class OfferEvaluationSnapshot {

	@Column(name = "buyer_utility", nullable = false, precision = 10, scale = 4)
	private BigDecimal buyerUtility;

	@Column(name = "estimated_supplier_utility", nullable = false, precision = 10, scale = 4)
	private BigDecimal estimatedSupplierUtility;

	@Column(name = "target_utility", nullable = false, precision = 10, scale = 4)
	private BigDecimal targetUtility;

	@Column(name = "continuation_value", nullable = false, precision = 10, scale = 4)
	private BigDecimal continuationValue;

	@Column(name = "nash_product", nullable = false, precision = 10, scale = 4)
	private BigDecimal nashProduct;

	protected OfferEvaluationSnapshot() {
	}

	public OfferEvaluationSnapshot(
		BigDecimal buyerUtility,
		BigDecimal estimatedSupplierUtility,
		BigDecimal targetUtility,
		BigDecimal continuationValue,
		BigDecimal nashProduct
	) {
		this.buyerUtility = buyerUtility;
		this.estimatedSupplierUtility = estimatedSupplierUtility;
		this.targetUtility = targetUtility;
		this.continuationValue = continuationValue;
		this.nashProduct = nashProduct;
	}

	public static OfferEvaluationSnapshot from(OfferEvaluation evaluation) {
		return new OfferEvaluationSnapshot(
			evaluation.buyerUtility(),
			evaluation.estimatedSupplierUtility(),
			evaluation.targetUtility(),
			evaluation.continuationValue(),
			evaluation.nashProduct());
	}

	public OfferEvaluation toOfferEvaluation() {
		return new OfferEvaluation(
			buyerUtility,
			estimatedSupplierUtility,
			targetUtility,
			continuationValue,
			nashProduct);
	}

	public BigDecimal getBuyerUtility() {
		return buyerUtility;
	}

	public BigDecimal getEstimatedSupplierUtility() {
		return estimatedSupplierUtility;
	}

	public BigDecimal getTargetUtility() {
		return targetUtility;
	}

	public BigDecimal getContinuationValue() {
		return continuationValue;
	}

	public BigDecimal getNashProduct() {
		return nashProduct;
	}
}
