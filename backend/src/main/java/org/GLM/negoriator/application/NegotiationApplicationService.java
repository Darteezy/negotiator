package org.GLM.negoriator.application;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;

import org.GLM.negoriator.domain.BuyerProfileSnapshot;
import org.GLM.negoriator.domain.NegotiationDecision;
import org.GLM.negoriator.domain.NegotiationDecisionType;
import org.GLM.negoriator.domain.NegotiationOffer;
import org.GLM.negoriator.domain.NegotiationParty;
import org.GLM.negoriator.domain.NegotiationSession;
import org.GLM.negoriator.domain.NegotiationSessionRepository;
import org.GLM.negoriator.domain.NegotiationSessionStatus;
import org.GLM.negoriator.domain.NegotiationStrategyChange;
import org.GLM.negoriator.domain.NegotiationStrategyChangeTrigger;
import org.GLM.negoriator.domain.NegotiationBoundsSnapshot;
import org.GLM.negoriator.domain.OfferEvaluationSnapshot;
import org.GLM.negoriator.domain.OfferTermsSnapshot;
import org.GLM.negoriator.domain.SupplierBeliefSnapshot;
import org.GLM.negoriator.domain.SupplierModelSnapshot;
import org.GLM.negoriator.negotiation.NegotiationEngine;
import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NegotiationApplicationService {

	private final NegotiationSessionRepository sessionRepository;
	private final NegotiationEngine negotiationEngine;
	private final StrategySwitchPolicy strategySwitchPolicy;

	public NegotiationApplicationService(
		NegotiationSessionRepository sessionRepository,
		NegotiationEngine negotiationEngine
	) {
		this.sessionRepository = sessionRepository;
		this.negotiationEngine = negotiationEngine;
		this.strategySwitchPolicy = new StrategySwitchPolicy();
	}

	public NegotiationSession startSession(StartSessionCommand command) {
		NegotiationSession session = new NegotiationSession(
			1,
			command.maxRounds(),
			command.strategy(),
			command.riskOfWalkaway(),
			NegotiationSessionStatus.PENDING,
			BuyerProfileSnapshot.from(command.buyerProfile()),
			NegotiationBoundsSnapshot.from(command.bounds()),
			SupplierModelSnapshot.from(command.supplierModel()));
		session.addStrategyChange(new NegotiationStrategyChange(
			1,
			null,
			command.strategy(),
			NegotiationStrategyChangeTrigger.INITIAL_SELECTION,
			"Session started with " + command.strategy().name() + " as the configured opening strategy."));

		return sessionRepository.saveAndFlush(session);
	}

	@Transactional(readOnly = true)
	public NegotiationSession getSession(UUID sessionId) {
		NegotiationSession session = sessionRepository.findDetailedById(sessionId)
			.orElseThrow(() -> new EntityNotFoundException("Negotiation session not found: " + sessionId));
		session.getOffers().size();
		session.getStrategyChanges().size();
		return session;
	}

	public NegotiationSession submitSupplierOffer(UUID sessionId, OfferVector supplierOfferTerms) {
		NegotiationSession session = sessionRepository.findById(sessionId)
			.orElseThrow(() -> new EntityNotFoundException("Negotiation session not found: " + sessionId));

		if (session.isClosed()) {
			throw new IllegalStateException("Negotiation session is closed: " + sessionId);
		}

		NegotiationOffer supplierOffer = new NegotiationOffer(
			session.getCurrentRound(),
			NegotiationParty.SUPPLIER,
			OfferTermsSnapshot.from(supplierOfferTerms));
		StrategySwitchPolicy.StrategyContext strategyContext = strategySwitchPolicy.describeCurrentStrategy(session);

		NegotiationEngine.NegotiationResponse response = negotiationEngine.negotiate(
			new NegotiationEngine.NegotiationRequest(
				supplierOfferTerms,
				session.toNegotiationContext(),
				session.toBuyerProfile(),
				session.currentSupplierModel(),
				session.toNegotiationBounds()));

		session.addOffer(supplierOffer);

		var buyerCounterOffers = response.counterOffers().stream()
			.map(offer -> new NegotiationOffer(
				session.getCurrentRound(),
				NegotiationParty.BUYER,
				OfferTermsSnapshot.from(offer)))
			.toList();

		NegotiationOffer counterOffer = buyerCounterOffers.stream()
			.findFirst()
			.orElse(null);

		for (NegotiationOffer buyerCounterOffer : buyerCounterOffers) {
			session.addOffer(buyerCounterOffer);
		}

		session.addDecision(new NegotiationDecision(
			supplierOffer.getRoundNumber(),
			NegotiationDecisionType.from(response.decision()),
			NegotiationSessionStatus.from(response.nextState()),
			supplierOffer,
			counterOffer,
			OfferEvaluationSnapshot.from(response.evaluation()),
			SupplierBeliefSnapshot.from(response.updatedSupplierBeliefs()),
			response.reasonCode(),
			response.focusIssue(),
			strategyContext.strategy(),
			strategyContext.rationale(),
			response.explanation()));

		StrategySwitchPolicy.StrategyCheckpoint strategyCheckpoint = strategySwitchPolicy.evaluate(
			session,
			response,
			supplierOfferTerms);
		if (strategyCheckpoint.switched()) {
			session.switchStrategy(
				strategyCheckpoint.nextStrategy(),
				supplierOffer.getRoundNumber(),
				strategyCheckpoint.trigger(),
				strategyCheckpoint.rationale());
		}
		session.moveTo(NegotiationSessionStatus.from(response.nextState()));

		return sessionRepository.saveAndFlush(session);
	}

	public record StartSessionCommand(
		NegotiationStrategy strategy,
		int maxRounds,
		BigDecimal riskOfWalkaway,
		BuyerProfile buyerProfile,
		NegotiationBounds bounds,
		SupplierModel supplierModel
	) {
	}
}
