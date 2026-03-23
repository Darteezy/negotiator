package org.GLM.negoriator.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
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

import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;

@Entity
@Table(name = "negotiation_strategy_changes")
public class NegotiationStrategyChange {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false)
	private NegotiationSession session;

	@Column(name = "round_number", nullable = false)
	private Integer roundNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "previous_strategy", length = 24)
	private NegotiationStrategy previousStrategy;

	@Enumerated(EnumType.STRING)
	@Column(name = "next_strategy", nullable = false, length = 24)
	private NegotiationStrategy nextStrategy;

	@Enumerated(EnumType.STRING)
	@Column(name = "trigger_type", nullable = false, length = 32)
	private NegotiationStrategyChangeTrigger trigger;

	@Column(name = "rationale", nullable = false, length = 1000)
	private String rationale;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected NegotiationStrategyChange() {
	}

	public NegotiationStrategyChange(
		Integer roundNumber,
		NegotiationStrategy previousStrategy,
		NegotiationStrategy nextStrategy,
		NegotiationStrategyChangeTrigger trigger,
		String rationale
	) {
		this.roundNumber = roundNumber;
		this.previousStrategy = previousStrategy;
		this.nextStrategy = nextStrategy;
		this.trigger = trigger;
		this.rationale = rationale;
	}

	void attachTo(NegotiationSession session) {
		this.session = session;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public UUID getId() {
		return id;
	}

	public Integer getRoundNumber() {
		return roundNumber;
	}

	public NegotiationStrategy getPreviousStrategy() {
		return previousStrategy;
	}

	public NegotiationStrategy getNextStrategy() {
		return nextStrategy;
	}

	public NegotiationStrategyChangeTrigger getTrigger() {
		return trigger;
	}

	public String getRationale() {
		return rationale;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}