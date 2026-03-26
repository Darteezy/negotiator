package org.GLM.negoriator.application;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;

import org.GLM.negoriator.ai.AiNegotiationMessageService;
import org.GLM.negoriator.ai.AiNegotiationMessageService.BuyerReplyMessageRequest;
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
	private final AiNegotiationMessageService aiNegotiationMessageService;
	private final SupplierMessageIntentParser supplierMessageIntentParser;
	private final SupplierMessageIntentAiFallbackService supplierMessageIntentAiFallbackService;

	public NegotiationApplicationService(
		NegotiationSessionRepository sessionRepository,
		NegotiationEngine negotiationEngine,
		AiNegotiationMessageService aiNegotiationMessageService,
		SupplierMessageIntentAiFallbackService supplierMessageIntentAiFallbackService
	) {
		this.sessionRepository = sessionRepository;
		this.negotiationEngine = negotiationEngine;
		this.sessionConfigurationValidator = new SessionConfigurationValidator();
		this.aiNegotiationMessageService = aiNegotiationMessageService;
		this.supplierMessageIntentParser = new SupplierMessageIntentParser();
		this.supplierMessageIntentAiFallbackService = supplierMessageIntentAiFallbackService;
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
			StrategyMetadata.initialSelectionRationale(command.strategy())));

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

		if (!hasConfigurationChanges(session, command)) {
			return session;
		}

		session.updateConfiguration(
			command.maxRounds(),
			command.riskOfWalkaway(),
			BuyerProfileSnapshot.from(command.buyerProfile()),
			NegotiationBoundsSnapshot.from(command.bounds()));

		var previousStrategy = session.getStrategy();
		String manualUpdateRationale = StrategyMetadata.manualChangeRationale(previousStrategy, command.strategy());

		if (session.getStrategy() != command.strategy()) {
			session.switchStrategy(
				command.strategy(),
				session.getCurrentRound(),
				NegotiationStrategyChangeTrigger.MANUAL_CONFIGURATION,
				manualUpdateRationale);
		} else {
			session.addStrategyChange(new NegotiationStrategyChange(
				session.getCurrentRound(),
				previousStrategy,
				previousStrategy,
				NegotiationStrategyChangeTrigger.MANUAL_CONFIGURATION,
				manualUpdateRationale));
		}

		return sessionRepository.saveAndFlush(session);
	}

	private boolean hasConfigurationChanges(NegotiationSession session, UpdateSessionSettingsCommand command) {
		return session.getStrategy() != command.strategy()
			|| session.getMaxRounds() != command.maxRounds()
			|| session.getRiskOfWalkaway().compareTo(command.riskOfWalkaway()) != 0
			|| !sameBuyerProfile(session.toBuyerProfile(), command.buyerProfile())
			|| !sameBounds(session.toNegotiationBounds(), command.bounds());
	}

	private boolean sameBuyerProfile(BuyerProfile left, BuyerProfile right) {
		return sameOffer(left.idealOffer(), right.idealOffer())
			&& sameOffer(left.reservationOffer(), right.reservationOffer())
			&& left.reservationUtility().compareTo(right.reservationUtility()) == 0
			&& left.weights().price().compareTo(right.weights().price()) == 0
			&& left.weights().paymentDays().compareTo(right.weights().paymentDays()) == 0
			&& left.weights().deliveryDays().compareTo(right.weights().deliveryDays()) == 0
			&& left.weights().contractMonths().compareTo(right.weights().contractMonths()) == 0;
	}

	private boolean sameBounds(NegotiationBounds left, NegotiationBounds right) {
		return left.minPrice().compareTo(right.minPrice()) == 0
			&& left.maxPrice().compareTo(right.maxPrice()) == 0
			&& left.minPaymentDays() == right.minPaymentDays()
			&& left.maxPaymentDays() == right.maxPaymentDays()
			&& left.minDeliveryDays() == right.minDeliveryDays()
			&& left.maxDeliveryDays() == right.maxDeliveryDays()
			&& left.minContractMonths() == right.minContractMonths()
			&& left.maxContractMonths() == right.maxContractMonths();
	}

	private boolean sameOffer(OfferVector left, OfferVector right) {
		return left.price().compareTo(right.price()) == 0
			&& left.paymentDays() == right.paymentDays()
			&& left.deliveryDays() == right.deliveryDays()
			&& left.contractMonths() == right.contractMonths();
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
		java.util.List<NegotiationOffer> activeBuyerOffers = activeBuyerOffers(session);
		SupplierMessageIntentParser.SupplierMessageIntent supplierIntent = resolveSupplierIntent(
			supplierMessage,
			supplierOfferTerms,
			activeBuyerOffers);

		NegotiationOffer supplierOffer = new NegotiationOffer(
			session.getCurrentRound(),
			NegotiationParty.SUPPLIER,
			OfferTermsSnapshot.from(supplierOfferTerms),
			supplierMessage);
		NegotiationOffer acceptedBuyerOffer = matchingActiveBuyerOffer(
			session,
			supplierOfferTerms,
			supplierMessage,
			supplierIntent,
			activeBuyerOffers);

		NegotiationEngine.NegotiationResponse response = negotiationEngine.negotiate(
			new NegotiationEngine.NegotiationRequest(
				supplierOfferTerms,
				session.toNegotiationContext(),
				session.toBuyerProfile(),
				session.currentSupplierModel(),
				session.toNegotiationBounds(),
				toEngineSupplierConstraints(activeConstraints)));

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

		ClarificationDirective clarificationDirective = clarificationDirective(
			session,
			supplierOfferTerms,
			supplierMessage,
			supplierIntent,
			activeBuyerOffers,
			acceptedBuyerOffer);

		session.addOffer(supplierOffer);

		var buyerCounterOffers = clarificationDirective != null
			? clarificationDirective.counterOffers()
			: requiresExplicitSupplierAcceptance
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
			.orElse(acceptedBuyerOffer);
		Integer resolvedSelectedBuyerOfferIndex = resolvedSelectedOfferIndex(activeBuyerOffers, supplierMessage, supplierIntent);
		String supplierIntentDetails = supplierIntentDetails(
			supplierIntent,
			acceptedBuyerOffer,
			clarificationDirective,
			activeBuyerOffers,
			resolvedSelectedBuyerOfferIndex);

		for (NegotiationOffer buyerCounterOffer : buyerCounterOffers) {
			session.addOffer(buyerCounterOffer);
		}

		NegotiationDecisionType decisionType = clarificationDirective != null
			? NegotiationDecisionType.COUNTER
			: NegotiationDecisionType.from(response.decision());
		NegotiationSessionStatus resultingStatus = clarificationDirective != null
			? NegotiationSessionStatus.COUNTERED
			: NegotiationSessionStatus.from(response.nextState());
		String buyerMessage = clarificationDirective != null
			? clarificationDirective.message()
			: aiNegotiationMessageService.composeBuyerReply(new BuyerReplyMessageRequest(
				supplierOffer.getRoundNumber(),
				session.getMaxRounds(),
				decisionType.name(),
				resultingStatus.name(),
				supplierMessage,
				supplierOfferTerms,
				counterOffer == null ? null : counterOffer.toOfferVector(),
				buyerCounterOffers.stream().map(NegotiationOffer::toOfferVector).toList(),
				response.reasonCode(),
				response.focusIssue(),
				session.getStrategy(),
				StrategyMetadata.rationaleFor(session.getStrategy()),
				response.evaluation()));

		session.addDecision(new NegotiationDecision(
			supplierOffer.getRoundNumber(),
			decisionType,
			resultingStatus,
			supplierOffer,
			counterOffer,
			OfferEvaluationSnapshot.from(response.evaluation()),
			SupplierBeliefSnapshot.from(response.updatedSupplierBeliefs()),
			clarificationDirective != null ? NegotiationEngine.DecisionReason.COUNTER_TO_CLOSE_GAP : response.reasonCode(),
			clarificationDirective != null ? null : response.focusIssue(),
			session.getStrategy(),
			StrategyMetadata.rationaleFor(session.getStrategy()),
			buyerMessage,
			supplierIntent.type().name(),
			supplierIntent.source().name(),
			resolvedSelectedBuyerOfferIndex,
			supplierIntentDetails));
		session.moveTo(resultingStatus);

		return sessionRepository.saveAndFlush(session);
	}

	private String supplierIntentDetails(
		SupplierMessageIntentParser.SupplierMessageIntent supplierIntent,
		NegotiationOffer acceptedBuyerOffer,
		ClarificationDirective clarificationDirective,
		java.util.List<NegotiationOffer> activeBuyerOffers,
		Integer resolvedSelectedBuyerOfferIndex
	) {
		if (clarificationDirective != null) {
			return "Supplier message remained unresolved after deterministic parsing"
				+ (supplierIntent.source() == SupplierMessageIntentParser.SupplierIntentSource.AI_FALLBACK
					? " and AI fallback"
					: "")
				+ ", so the backend requested clarification against the current buyer offer context.";
		}

		if (acceptedBuyerOffer != null && supplierIntent.type() == SupplierMessageIntentParser.SupplierIntentType.UNCLEAR) {
			return "Supplier terms exactly matched an active buyer offer, so the round was treated as acceptance even though the message text itself remained unclear.";
		}

		return switch (supplierIntent.type()) {
			case ACCEPT_ACTIVE_OFFER -> resolvedSelectedBuyerOfferIndex == null
				? activeBuyerOffers.size() == 1
					? "Supplier message was interpreted as accepting the active buyer offer."
					: "Supplier message was interpreted as accepting the buyer's active offer context."
				: "Supplier message was interpreted as accepting buyer option " + resolvedSelectedBuyerOfferIndex + ".";
			case SELECT_COUNTER_OPTION -> resolvedSelectedBuyerOfferIndex == null
				? "Supplier message referenced one of the buyer's active options without final acceptance."
				: "Supplier message was interpreted as selecting buyer option " + resolvedSelectedBuyerOfferIndex + " without final acceptance.";
			case PROPOSE_NEW_TERMS -> "Supplier message was interpreted as a fresh supplier counterproposal.";
			case REJECT_OR_DECLINE -> "Supplier message was interpreted as declining the buyer's current position.";
			case UNCLEAR -> "Supplier message remained unresolved by deterministic parsing.";
		};
	}

	private SupplierMessageIntentParser.SupplierMessageIntent resolveSupplierIntent(
		String supplierMessage,
		OfferVector supplierOfferTerms,
		java.util.List<NegotiationOffer> activeBuyerOffers
	) {
		SupplierMessageIntentParser.SupplierMessageIntent supplierIntent = supplierMessageIntentParser.parse(supplierMessage);
		if (supplierIntent.type() != SupplierMessageIntentParser.SupplierIntentType.UNCLEAR || activeBuyerOffers.isEmpty()) {
			return supplierIntent;
		}

		return supplierMessageIntentAiFallbackService.resolve(
			supplierMessage,
			supplierOfferTerms,
			activeBuyerOffers.stream().map(NegotiationOffer::toOfferVector).toList())
			.orElse(supplierIntent);
	}

	private java.util.List<NegotiationOffer> activeBuyerOffers(NegotiationSession session) {
		if (session.getCurrentRound() <= 1) {
			return java.util.List.of();
		}

		int activeBuyerRound = session.getCurrentRound() - 1;
		return session.getOffers().stream()
			.filter(offer -> offer.getParty() == NegotiationParty.BUYER)
			.filter(offer -> offer.getRoundNumber() == activeBuyerRound)
			.sorted(java.util.Comparator.comparing(NegotiationOffer::getCreatedAt))
			.toList();
	}

	private ClarificationDirective clarificationDirective(
		NegotiationSession session,
		OfferVector supplierOfferTerms,
		String supplierMessage,
		SupplierMessageIntentParser.SupplierMessageIntent supplierIntent,
		java.util.List<NegotiationOffer> activeBuyerOffers,
		NegotiationOffer acceptedBuyerOffer
	) {
		if (supplierIntent.type() != SupplierMessageIntentParser.SupplierIntentType.UNCLEAR
			|| acceptedBuyerOffer != null
			|| activeBuyerOffers.isEmpty()) {
			return null;
		}

		java.util.List<NegotiationOffer> repeatedBuyerOffers = activeBuyerOffers.stream()
			.map(offer -> new NegotiationOffer(
				session.getCurrentRound(),
				NegotiationParty.BUYER,
				OfferTermsSnapshot.from(offer.toOfferVector())))
			.toList();

		String message = repeatedBuyerOffers.size() > 1
			? buildMultiOptionClarificationMessage(repeatedBuyerOffers)
			: buildSingleOfferClarificationMessage(repeatedBuyerOffers.getFirst().toOfferVector(), supplierMessage, supplierOfferTerms);

		return new ClarificationDirective(repeatedBuyerOffers, message);
	}

	private String buildMultiOptionClarificationMessage(java.util.List<NegotiationOffer> repeatedBuyerOffers) {
		StringBuilder sb = new StringBuilder("Thank you for the update. Please confirm which buyer option is closest on your side:\n");
		for (int index = 0; index < repeatedBuyerOffers.size(); index++) {
			sb.append("- Option ").append(index + 1).append(": ")
				.append(formatOfferForClarification(repeatedBuyerOffers.get(index).toOfferVector()));
			if (index + 1 < repeatedBuyerOffers.size()) {
				sb.append('\n');
			}
		}
		return sb.toString();
	}

	private String buildSingleOfferClarificationMessage(
		OfferVector activeBuyerOffer,
		String supplierMessage,
		OfferVector supplierOfferTerms
	) {
		return "Thank you for the update. Please confirm whether you accept our current terms of "
			+ formatOfferForClarification(activeBuyerOffer)
			+ " or specify which commercial point you want to revise.";
	}

	private String formatOfferForClarification(OfferVector offer) {
		return "price " + offer.price()
			+ ", payment " + offer.paymentDays() + " days"
			+ ", delivery " + offer.deliveryDays() + " days"
			+ ", contract " + offer.contractMonths() + " months";
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

	private NegotiationEngine.SupplierConstraints toEngineSupplierConstraints(
		SupplierConstraintsSnapshot supplierConstraints
	) {
		if (supplierConstraints == null || supplierConstraints.isEmpty()) {
			return null;
		}

		return new NegotiationEngine.SupplierConstraints(
			supplierConstraints.getPriceFloor(),
			supplierConstraints.getPaymentDaysCeiling(),
			supplierConstraints.getDeliveryDaysFloor(),
			supplierConstraints.getContractMonthsFloor());
	}

	private NegotiationOffer matchingActiveBuyerOffer(
		NegotiationSession session,
		OfferVector supplierOfferTerms,
		String supplierMessage,
		SupplierMessageIntentParser.SupplierMessageIntent supplierIntent,
		java.util.List<NegotiationOffer> activeBuyerOffers
	) {
		if (session.getCurrentRound() <= 1 || activeBuyerOffers.isEmpty()) {
			return null;
		}

		if (session.getStrategy() == NegotiationStrategy.MESO && supplierIntent.selectsCounterOption()) {
			return null;
		}

		NegotiationOffer exactMatch = activeBuyerOffers.stream()
			.filter(offer -> offer.toOfferVector().matches(supplierOfferTerms))
			.findFirst()
			.orElse(null);
		if (supplierIntent.proposesNewTerms() || supplierIntent.rejectsOrDeclines()) {
			return null;
		}

		if (exactMatch != null) {
			return exactMatch;
		}

		if (supplierIntent.selectsCounterOption()) {
			return null;
		}

		Integer selectedOfferIndex = resolvedSelectedOfferIndex(activeBuyerOffers, supplierMessage, supplierIntent);
		if (selectedOfferIndex != null
			&& selectedOfferIndex >= 1
			&& selectedOfferIndex <= activeBuyerOffers.size()) {
			if (supplierIntent.acceptsBuyerOffer()) {
				return activeBuyerOffers.get(selectedOfferIndex - 1);
			}
			return activeBuyerOffers.get(selectedOfferIndex - 1);
		}

		if (supplierIntent.acceptsBuyerOffer()) {
			if (activeBuyerOffers.size() == 1) {
				return activeBuyerOffers.getFirst();
			}
			if (session.getStrategy() == NegotiationStrategy.MESO) {
				// Meso may keep several live options, but the first buyer offer remains the primary settlement path.
				return activeBuyerOffers.getFirst();
			}
		}

		return null;
	}

	private Integer resolvedSelectedOfferIndex(
		java.util.List<NegotiationOffer> activeBuyerOffers,
		String supplierMessage,
		SupplierMessageIntentParser.SupplierMessageIntent supplierIntent
	) {
		if (supplierIntent.selectedBuyerOfferIndex() != null) {
			return supplierIntent.selectedBuyerOfferIndex();
		}

		return descriptiveOfferIndex(activeBuyerOffers, supplierMessage);
	}

	private Integer descriptiveOfferIndex(
		java.util.List<NegotiationOffer> activeBuyerOffers,
		String supplierMessage
	) {
		if (supplierMessage == null || supplierMessage.isBlank() || activeBuyerOffers.isEmpty()) {
			return null;
		}

		String normalized = supplierMessage.toLowerCase(java.util.Locale.ROOT);
		if (referencesPriceDescriptor(normalized)) {
			return uniqueExtremumIndex(
				activeBuyerOffers,
				java.util.Comparator.comparing((NegotiationOffer offer) -> offer.toOfferVector().price()));
		}

		if (referencesPaymentDescriptor(normalized)) {
			return uniqueExtremumIndex(
				activeBuyerOffers,
				java.util.Comparator.comparingInt((NegotiationOffer offer) -> offer.toOfferVector().paymentDays()).reversed());
		}

		if (referencesDeliveryDescriptor(normalized)) {
			return uniqueExtremumIndex(
				activeBuyerOffers,
				java.util.Comparator.comparingInt((NegotiationOffer offer) -> offer.toOfferVector().deliveryDays()));
		}

		if (referencesContractDescriptor(normalized)) {
			return uniqueExtremumIndex(
				activeBuyerOffers,
				java.util.Comparator.comparingInt((NegotiationOffer offer) -> offer.toOfferVector().contractMonths()));
		}

		return null;
	}

	private boolean referencesPriceDescriptor(String normalized) {
		return (normalized.contains("lower price") || normalized.contains("lowest price")
			|| normalized.contains("cheaper price") || normalized.contains("cheapest price")
			|| normalized.contains("higher price") || normalized.contains("highest price"))
			&& containsOfferReferenceWord(normalized);
	}

	private boolean referencesPaymentDescriptor(String normalized) {
		return (normalized.contains("longer payment") || normalized.contains("longest payment")
			|| normalized.contains("higher payment"))
			&& containsOfferReferenceWord(normalized);
	}

	private boolean referencesDeliveryDescriptor(String normalized) {
		return (normalized.contains("faster delivery") || normalized.contains("fastest delivery")
			|| normalized.contains("quicker delivery") || normalized.contains("quickest delivery")
			|| normalized.contains("earlier delivery") || normalized.contains("shorter delivery"))
			&& containsOfferReferenceWord(normalized);
	}

	private boolean referencesContractDescriptor(String normalized) {
		return (normalized.contains("shorter contract") || normalized.contains("shortest contract")
			|| normalized.contains("longer contract") || normalized.contains("longest contract"))
			&& containsOfferReferenceWord(normalized);
	}

	private boolean containsOfferReferenceWord(String normalized) {
		return normalized.contains("option")
			|| normalized.contains("offer")
			|| normalized.contains("package")
			|| normalized.contains("structure")
			|| normalized.contains("version");
	}

	private Integer uniqueExtremumIndex(
		java.util.List<NegotiationOffer> activeBuyerOffers,
		java.util.Comparator<NegotiationOffer> comparator
	) {
		java.util.List<NegotiationOffer> sortedOffers = activeBuyerOffers.stream()
			.sorted(comparator)
			.toList();
		if (sortedOffers.isEmpty()) {
			return null;
		}

		NegotiationOffer bestOffer = sortedOffers.getFirst();
		if (sortedOffers.size() > 1 && comparator.compare(bestOffer, sortedOffers.get(1)) == 0) {
			return null;
		}

		return activeBuyerOffers.indexOf(bestOffer) + 1;
	}

	private record ClarificationDirective(
		java.util.List<NegotiationOffer> counterOffers,
		String message
	) {
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
