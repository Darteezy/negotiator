package org.GLM.negoriator.application;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;

import org.GLM.negoriator.ai.AiStrategyAdvisor;
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
import org.GLM.negoriator.domain.SupplierConstraintsSnapshot;
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
	private final SessionConfigurationValidator sessionConfigurationValidator;

	public NegotiationApplicationService(
		NegotiationSessionRepository sessionRepository,
		NegotiationEngine negotiationEngine,
		AiStrategyAdvisor aiStrategyAdvisor
	) {
		this.sessionRepository = sessionRepository;
		this.negotiationEngine = negotiationEngine;
		this.sessionConfigurationValidator = new SessionConfigurationValidator();
	}

	public NegotiationSession startSession(StartSessionCommand command) {
		sessionConfigurationValidator.validate(command);

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
		session.getDecisions().size();
		session.getStrategyChanges().size();
		return session;
	}

	public NegotiationSession updateSessionSettings(UUID sessionId, UpdateSessionSettingsCommand command) {
		NegotiationSession session = sessionRepository.findDetailedByIdForUpdate(sessionId)
			.orElseThrow(() -> new EntityNotFoundException("Negotiation session not found: " + sessionId));
		session.getOffers().size();
		session.getDecisions().size();
		session.getStrategyChanges().size();

		if (session.isClosed()) {
			throw new IllegalStateException("Negotiation session is closed: " + sessionId);
		}

		if (command.maxRounds() < session.getCurrentRound()) {
			throw new IllegalArgumentException("maxRounds must be greater than or equal to the current round.");
		}

		sessionConfigurationValidator.validate(new StartSessionCommand(
			command.strategy(),
			command.maxRounds(),
			command.riskOfWalkaway(),
			command.buyerProfile(),
			command.bounds(),
			session.initialSupplierModel()));

		session.updateConfiguration(
			command.maxRounds(),
			command.riskOfWalkaway(),
			BuyerProfileSnapshot.from(command.buyerProfile()),
			NegotiationBoundsSnapshot.from(command.bounds()));

		if (session.getStrategy() != command.strategy()) {
			session.switchStrategy(
				command.strategy(),
				session.getCurrentRound(),
				NegotiationStrategyChangeTrigger.MANUAL_CONFIGURATION,
				"Session settings updated manually. Upcoming rounds will use "
					+ command.strategy().name()
					+ ".");
		}

		return sessionRepository.saveAndFlush(session);
	}

	public NegotiationSession submitSupplierOffer(UUID sessionId, OfferVector supplierOfferTerms) {
		return submitSupplierOffer(sessionId, supplierOfferTerms, null, null);
	}

	public NegotiationSession submitSupplierOffer(
		UUID sessionId,
		OfferVector supplierOfferTerms,
		SupplierConstraintsSnapshot supplierConstraints,
		String supplierMessage
	) {
		NegotiationSession session = sessionRepository.findDetailedByIdForUpdate(sessionId)
			.orElseThrow(() -> new EntityNotFoundException("Negotiation session not found: " + sessionId));
		session.getOffers().size();
		session.getDecisions().size();
		session.getStrategyChanges().size();

		if (session.isClosed()) {
			throw new IllegalStateException("Negotiation session is closed: " + sessionId);
		}

		session.mergeSupplierConstraints(supplierConstraints);
		SupplierConstraintsSnapshot activeConstraints = session.getSupplierConstraintsSnapshot();

		NegotiationOffer supplierOffer = new NegotiationOffer(
			session.getCurrentRound(),
			NegotiationParty.SUPPLIER,
			OfferTermsSnapshot.from(supplierOfferTerms),
			supplierMessage);
		NegotiationOffer acceptedBuyerOffer = matchingActiveBuyerOffer(session, supplierOfferTerms, supplierMessage);

		NegotiationEngine.NegotiationResponse response = negotiationEngine.negotiate(
			new NegotiationEngine.NegotiationRequest(
				supplierOfferTerms,
				session.toNegotiationContext(),
				session.toBuyerProfile(),
				session.currentSupplierModel(),
				session.toNegotiationBounds()));

		if (acceptedBuyerOffer != null
			&& response.decision() != NegotiationEngine.Decision.REJECT
			&& response.evaluation().buyerUtility().compareTo(session.toBuyerProfile().reservationUtility()) >= 0) {
			response = new NegotiationEngine.NegotiationResponse(
				NegotiationEngine.Decision.ACCEPT,
				NegotiationEngine.NegotiationState.ACCEPTED,
				java.util.List.of(),
				response.evaluation(),
				response.updatedSupplierBeliefs(),
				NegotiationEngine.DecisionReason.TARGET_UTILITY_MET,
				null,
				"Accepted because the supplier agreed to the buyer's active offer from the previous round.");
		}
		boolean requiresExplicitSupplierAcceptance = acceptedBuyerOffer == null
			&& response.decision() == NegotiationEngine.Decision.ACCEPT;

		if (requiresExplicitSupplierAcceptance) {
			response = new NegotiationEngine.NegotiationResponse(
				NegotiationEngine.Decision.COUNTER,
				NegotiationEngine.NegotiationState.COUNTERED,
				java.util.List.of(supplierOfferTerms),
				response.evaluation(),
				response.updatedSupplierBeliefs(),
				response.reasonCode(),
				null,
				"Buyer is ready to close on these terms. Reply with accept to finalize the deal.");
		}

		session.addOffer(supplierOffer);

		var buyerCounterOffers = requiresExplicitSupplierAcceptance
			? java.util.List.of(new NegotiationOffer(
				session.getCurrentRound(),
				NegotiationParty.BUYER,
				OfferTermsSnapshot.from(supplierOfferTerms)))
			: applySupplierConstraints(response.counterOffers(), supplierOfferTerms, activeConstraints).stream()
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
			session.getStrategy(),
			"Baseline policy remained active for this round.",
			response.explanation()));
		session.moveTo(NegotiationSessionStatus.from(response.nextState()));

		return sessionRepository.saveAndFlush(session);
	}

	private java.util.List<OfferVector> applySupplierConstraints(
		java.util.List<OfferVector> counterOffers,
		OfferVector supplierOfferTerms,
		SupplierConstraintsSnapshot supplierConstraints
	) {
		if (counterOffers.isEmpty() || supplierConstraints == null || supplierConstraints.isEmpty()) {
			return counterOffers;
		}

		java.util.List<OfferVector> feasibleOffers = new java.util.ArrayList<>();

		for (OfferVector counterOffer : counterOffers) {
			OfferVector constrainedOffer = supplierConstraints.clamp(counterOffer);

			if (constrainedOffer.matches(supplierOfferTerms)) {
				continue;
			}

			if (feasibleOffers.stream().noneMatch(existing -> existing.matches(constrainedOffer))) {
				feasibleOffers.add(constrainedOffer);
			}
		}

		return feasibleOffers;
	}

	private NegotiationOffer matchingActiveBuyerOffer(
		NegotiationSession session,
		OfferVector supplierOfferTerms,
		String supplierMessage
	) {
		if (session.getCurrentRound() <= 1) {
			return null;
		}

		if (!isExplicitAcceptanceMessage(supplierMessage)) {
			return null;
		}

		int activeBuyerRound = session.getCurrentRound() - 1;

		return session.getOffers().stream()
			.filter(offer -> offer.getParty() == NegotiationParty.BUYER)
			.filter(offer -> offer.getRoundNumber() == activeBuyerRound)
			.filter(offer -> offer.toOfferVector().matches(supplierOfferTerms))
			.findFirst()
			.orElse(null);
	}

	private boolean isExplicitAcceptanceMessage(String supplierMessage) {
		if (supplierMessage == null || supplierMessage.isBlank()) {
			return true;
		}

		String normalized = supplierMessage.toLowerCase(java.util.Locale.ROOT);
		if (normalized.contains("counter")
			|| normalized.contains("i propose")
			|| normalized.contains("i offer")
			|| normalized.contains("final offer")
			|| normalized.contains("if you accept")
			|| normalized.contains("cannot")
			|| normalized.contains("can't")
			|| normalized.contains("not settling")) {
			return false;
		}

		return normalized.contains("accept")
			|| normalized.contains("agreed")
			|| normalized.contains("confirm")
			|| normalized.contains("works for us")
			|| normalized.contains("we can proceed")
			|| normalized.contains("we have a deal");
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

	public record UpdateSessionSettingsCommand(
		NegotiationStrategy strategy,
		int maxRounds,
		BigDecimal riskOfWalkaway,
		BuyerProfile buyerProfile,
		NegotiationBounds bounds
	) {
	}
}
