package org.GLM.negoriator.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import org.GLM.negoriator.negotiation.NegotiationEngine.OfferEvaluation;
import org.GLM.negoriator.negotiation.NegotiationEngine.DecisionReason;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationIssue;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierModel;

@Entity
@Table(name = "negotiation_decisions")
public class NegotiationDecision {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false)
	private NegotiationSession session;

	@Column(name = "round_number", nullable = false)
	private Integer roundNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "decision", nullable = false, length = 16)
	private NegotiationDecisionType decision;

	@Enumerated(EnumType.STRING)
	@Column(name = "resulting_status", nullable = false, length = 16)
	private NegotiationSessionStatus resultingStatus;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "supplier_offer_id")
	private NegotiationOffer supplierOffer;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "counter_offer_id")
	private NegotiationOffer counterOffer;

	@Embedded
	private OfferEvaluationSnapshot evaluation;

	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "marginFocused", column = @Column(name = "updated_belief_margin_focused", nullable = false, precision = 10, scale = 4)),
		@AttributeOverride(name = "cashflowFocused", column = @Column(name = "updated_belief_cashflow_focused", nullable = false, precision = 10, scale = 4)),
		@AttributeOverride(name = "operationsFocused", column = @Column(name = "updated_belief_operations_focused", nullable = false, precision = 10, scale = 4)),
		@AttributeOverride(name = "stabilityFocused", column = @Column(name = "updated_belief_stability_focused", nullable = false, precision = 10, scale = 4))
	})
	private SupplierBeliefSnapshot updatedSupplierBeliefs;

	@Enumerated(EnumType.STRING)
	@Column(name = "reason_code", nullable = false, length = 40)
	private DecisionReason reasonCode;

	@Enumerated(EnumType.STRING)
	@Column(name = "focus_issue", length = 32)
	private NegotiationIssue focusIssue;

	@Enumerated(EnumType.STRING)
	@Column(name = "strategy_used", nullable = false, length = 24)
	private NegotiationStrategy strategyUsed;

	@Column(name = "strategy_rationale", nullable = false, length = 1000)
	private String strategyRationale;

	@Column(name = "explanation", nullable = false, length = 1000)
	private String explanation;

	@Column(name = "supplier_intent_type", length = 40)
	private String supplierIntentType;

	@Column(name = "supplier_intent_source", length = 24)
	private String supplierIntentSource;

	@Column(name = "supplier_selected_buyer_offer_index")
	private Integer supplierSelectedBuyerOfferIndex;

	@Column(name = "supplier_intent_details", length = 1000)
	private String supplierIntentDetails;

	@Column(name = "decided_at", nullable = false)
	private Instant decidedAt;

	protected NegotiationDecision() {
	}

	public NegotiationDecision(
		Integer roundNumber,
		NegotiationDecisionType decision,
		NegotiationSessionStatus resultingStatus,
		NegotiationOffer supplierOffer,
		NegotiationOffer counterOffer,
		OfferEvaluationSnapshot evaluation,
		SupplierBeliefSnapshot updatedSupplierBeliefs,
		DecisionReason reasonCode,
		NegotiationIssue focusIssue,
		NegotiationStrategy strategyUsed,
		String strategyRationale,
		String explanation,
		String supplierIntentType,
		String supplierIntentSource,
		Integer supplierSelectedBuyerOfferIndex,
		String supplierIntentDetails
	) {
		this.roundNumber = roundNumber;
		this.decision = decision;
		this.resultingStatus = resultingStatus;
		this.supplierOffer = supplierOffer;
		this.counterOffer = counterOffer;
		this.evaluation = evaluation;
		this.updatedSupplierBeliefs = updatedSupplierBeliefs;
		this.reasonCode = reasonCode;
		this.focusIssue = focusIssue;
		this.strategyUsed = strategyUsed;
		this.strategyRationale = strategyRationale;
		this.explanation = explanation;
		this.supplierIntentType = supplierIntentType;
		this.supplierIntentSource = supplierIntentSource;
		this.supplierSelectedBuyerOfferIndex = supplierSelectedBuyerOfferIndex;
		this.supplierIntentDetails = supplierIntentDetails;
	}

	void attachTo(NegotiationSession session) {
		this.session = session;
	}

	public OfferEvaluation toOfferEvaluation() {
		return evaluation.toOfferEvaluation();
	}

	public SupplierModel toSupplierModel(BigDecimal reservationUtility) {
		return new SupplierModel(updatedSupplierBeliefs.toBeliefMap(), reservationUtility);
	}

	@PrePersist
	void onCreate() {
		if (decidedAt == null) {
			decidedAt = Instant.now();
		}
	}

	public UUID getId() {
		return id;
	}

	public NegotiationSession getSession() {
		return session;
	}

	public Integer getRoundNumber() {
		return roundNumber;
	}

	public NegotiationDecisionType getDecision() {
		return decision;
	}

	public NegotiationSessionStatus getResultingStatus() {
		return resultingStatus;
	}

	public NegotiationOffer getSupplierOffer() {
		return supplierOffer;
	}

	public NegotiationOffer getCounterOffer() {
		return counterOffer;
	}

	public OfferEvaluationSnapshot getEvaluation() {
		return evaluation;
	}

	public SupplierBeliefSnapshot getUpdatedSupplierBeliefs() {
		return updatedSupplierBeliefs;
	}

	public DecisionReason getReasonCode() {
		return reasonCode;
	}

	public NegotiationIssue getFocusIssue() {
		return focusIssue;
	}

	public NegotiationStrategy getStrategyUsed() {
		return strategyUsed;
	}

	public String getStrategyRationale() {
		return strategyRationale;
	}

	public String getExplanation() {
		return explanation;
	}

	public String getSupplierIntentType() {
		return supplierIntentType;
	}

	public String getSupplierIntentSource() {
		return supplierIntentSource;
	}

	public Integer getSupplierSelectedBuyerOfferIndex() {
		return supplierSelectedBuyerOfferIndex;
	}

	public String getSupplierIntentDetails() {
		return supplierIntentDetails;
	}

	public Instant getDecidedAt() {
		return decidedAt;
	}
}
