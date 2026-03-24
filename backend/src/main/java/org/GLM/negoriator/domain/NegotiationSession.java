package org.GLM.negoriator.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationContext;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierModel;

@Entity
@Table(name = "negotiation_sessions")
public class NegotiationSession {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Version
	@Column(name = "version", nullable = false)
	private Long version;

	@Column(name = "current_round", nullable = false)
	private Integer currentRound;

	@Column(name = "max_rounds", nullable = false)
	private Integer maxRounds;

	@Enumerated(EnumType.STRING)
	@Column(name = "strategy", nullable = false, length = 24)
	private NegotiationStrategy strategy;

	@Column(name = "risk_of_walkaway", nullable = false, precision = 10, scale = 4)
	private BigDecimal riskOfWalkaway;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	private NegotiationSessionStatus status;

	@Column(name = "session_token", nullable = false, unique = true, length = 64)
	private String sessionToken;

	@Embedded
	private BuyerProfileSnapshot buyerProfileSnapshot;

	@Embedded
	private NegotiationBoundsSnapshot boundsSnapshot;

	@Embedded
	private SupplierModelSnapshot supplierModelSnapshot;

	@Embedded
	private SupplierConstraintsSnapshot supplierConstraintsSnapshot;

	@OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@OrderBy("roundNumber ASC, createdAt ASC")
	private final List<NegotiationOffer> offers = new ArrayList<>();

	@OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@OrderBy("roundNumber ASC, decidedAt ASC")
	private final List<NegotiationDecision> decisions = new ArrayList<>();

	@OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@OrderBy("roundNumber ASC, createdAt ASC")
	private final List<NegotiationStrategyChange> strategyChanges = new ArrayList<>();

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected NegotiationSession() {
	}

	public NegotiationSession(
		Integer currentRound,
		Integer maxRounds,
		NegotiationStrategy strategy,
		BigDecimal riskOfWalkaway,
		NegotiationSessionStatus status,
		BuyerProfileSnapshot buyerProfileSnapshot,
		NegotiationBoundsSnapshot boundsSnapshot,
		SupplierModelSnapshot supplierModelSnapshot
	) {
		this.currentRound = currentRound;
		this.maxRounds = maxRounds;
		this.strategy = strategy;
		this.riskOfWalkaway = riskOfWalkaway;
		this.status = status;
		this.sessionToken = UUID.randomUUID().toString();
		this.buyerProfileSnapshot = buyerProfileSnapshot;
		this.boundsSnapshot = boundsSnapshot;
		this.supplierModelSnapshot = supplierModelSnapshot;
		this.supplierConstraintsSnapshot = SupplierConstraintsSnapshot.empty();
	}

	public void addOffer(NegotiationOffer offer) {
		offer.attachTo(this);
		offers.add(offer);
	}

	public void addDecision(NegotiationDecision decision) {
		decision.attachTo(this);
		decisions.add(decision);
	}

	public void addStrategyChange(NegotiationStrategyChange strategyChange) {
		strategyChange.attachTo(this);
		strategyChanges.add(strategyChange);
	}

	public BuyerProfile toBuyerProfile() {
		return buyerProfileSnapshot.toBuyerProfile();
	}

	public NegotiationBounds toNegotiationBounds() {
		return boundsSnapshot.toNegotiationBounds();
	}

	public SupplierModel initialSupplierModel() {
		return supplierModelSnapshot.toSupplierModel();
	}

	public SupplierConstraintsSnapshot supplierConstraints() {
		return supplierConstraintsSnapshot == null ? SupplierConstraintsSnapshot.empty() : supplierConstraintsSnapshot;
	}

	public void mergeSupplierConstraints(SupplierConstraintsSnapshot nextConstraints) {
		if (nextConstraints == null || nextConstraints.isEmpty()) {
			return;
		}

		supplierConstraintsSnapshot = supplierConstraints().merge(nextConstraints);
	}

	public SupplierModel currentSupplierModel() {
		if (decisions.isEmpty()) {
			return initialSupplierModel();
		}

		NegotiationDecision latestDecision = decisions.stream()
			.max(Comparator.comparing(NegotiationDecision::getRoundNumber)
				.thenComparing(NegotiationDecision::getDecidedAt))
			.orElseThrow();

		return latestDecision.toSupplierModel(
			supplierModelSnapshot.getReservationUtility());
	}

	public NegotiationContext toNegotiationContext() {
		List<OfferVector> history = new ArrayList<>();

		Map<Integer, List<NegotiationOffer>> offersByRound = offers.stream()
			.sorted(Comparator.comparing(NegotiationOffer::getRoundNumber)
				.thenComparing(NegotiationOffer::getCreatedAt))
			.collect(java.util.stream.Collectors.groupingBy(
				NegotiationOffer::getRoundNumber,
				java.util.LinkedHashMap::new,
				java.util.stream.Collectors.toList()));

		for (List<NegotiationOffer> roundOffers : offersByRound.values()) {
			for (NegotiationOffer offer : roundOffers) {
				if (offer.getParty() == NegotiationParty.SUPPLIER) {
					history.add(offer.toOfferVector());
				} else if (offer.getParty() == NegotiationParty.BUYER) {
					history.add(offer.toOfferVector());
					break;
				}
			}
		}

		return new NegotiationContext(
			currentRound,
			maxRounds,
			strategy,
			status.toNegotiationState(),
			riskOfWalkaway,
			history);
	}

	public void moveTo(NegotiationSessionStatus nextStatus) {
		this.status = nextStatus;
		if (nextStatus == NegotiationSessionStatus.COUNTERED) {
			this.currentRound = currentRound + 1;
		}
	}

	public void switchStrategy(
		NegotiationStrategy nextStrategy,
		Integer roundNumber,
		NegotiationStrategyChangeTrigger trigger,
		String rationale
	) {
		NegotiationStrategy previousStrategy = this.strategy;
		this.strategy = nextStrategy;
		addStrategyChange(new NegotiationStrategyChange(roundNumber, previousStrategy, nextStrategy, trigger, rationale));
	}

	public boolean isClosed() {
		return status == NegotiationSessionStatus.ACCEPTED
			|| status == NegotiationSessionStatus.REJECTED
			|| status == NegotiationSessionStatus.EXPIRED;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		if (sessionToken == null || sessionToken.isBlank()) {
			sessionToken = UUID.randomUUID().toString();
		}
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public Integer getCurrentRound() {
		return currentRound;
	}

	public Integer getMaxRounds() {
		return maxRounds;
	}

	public NegotiationStrategy getStrategy() {
		return strategy;
	}

	public BigDecimal getRiskOfWalkaway() {
		return riskOfWalkaway;
	}

	public NegotiationSessionStatus getStatus() {
		return status;
	}

	public String getSessionToken() {
		return sessionToken;
	}

	public BuyerProfileSnapshot getBuyerProfileSnapshot() {
		return buyerProfileSnapshot;
	}

	public NegotiationBoundsSnapshot getBoundsSnapshot() {
		return boundsSnapshot;
	}

	public SupplierModelSnapshot getSupplierModelSnapshot() {
		return supplierModelSnapshot;
	}

	public SupplierConstraintsSnapshot getSupplierConstraintsSnapshot() {
		return supplierConstraints();
	}

	public List<NegotiationOffer> getOffers() {
		return offers;
	}

	public List<NegotiationDecision> getDecisions() {
		return decisions;
	}

	public List<NegotiationStrategyChange> getStrategyChanges() {
		return strategyChanges;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
