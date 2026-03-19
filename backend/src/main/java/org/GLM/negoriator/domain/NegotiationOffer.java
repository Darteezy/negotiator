package org.GLM.negoriator.domain;

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

import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;

@Entity
@Table(name = "negotiation_offers")
public class NegotiationOffer {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false)
	private NegotiationSession session;

	@Column(name = "round_number", nullable = false)
	private Integer roundNumber;

	@Enumerated(EnumType.STRING)
	@Column(name = "party", nullable = false, length = 16)
	private NegotiationParty party;

	@Embedded
	@AttributeOverrides({
		@AttributeOverride(name = "price", column = @Column(name = "offer_price", nullable = false, precision = 19, scale = 4)),
		@AttributeOverride(name = "paymentDays", column = @Column(name = "offer_payment_days", nullable = false)),
		@AttributeOverride(name = "deliveryDays", column = @Column(name = "offer_delivery_days", nullable = false)),
		@AttributeOverride(name = "contractMonths", column = @Column(name = "offer_contract_months", nullable = false))
	})
	private OfferTermsSnapshot terms;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected NegotiationOffer() {
	}

	public NegotiationOffer(Integer roundNumber, NegotiationParty party, OfferTermsSnapshot terms) {
		this.roundNumber = roundNumber;
		this.party = party;
		this.terms = terms;
	}

	public OfferVector toOfferVector() {
		return terms.toOfferVector();
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

	public NegotiationSession getSession() {
		return session;
	}

	public Integer getRoundNumber() {
		return roundNumber;
	}

	public NegotiationParty getParty() {
		return party;
	}

	public OfferTermsSnapshot getTerms() {
		return terms;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
