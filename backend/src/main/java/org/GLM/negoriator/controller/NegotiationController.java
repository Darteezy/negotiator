package org.GLM.negoriator.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.GLM.negoriator.application.NegotiationApplicationService;
import org.GLM.negoriator.application.NegotiationDefaults;
import org.GLM.negoriator.application.StrategyMetadata;
import org.GLM.negoriator.domain.NegotiationDecision;
import org.GLM.negoriator.domain.NegotiationParty;
import org.GLM.negoriator.domain.NegotiationSession;
import org.GLM.negoriator.domain.NegotiationStrategyChange;
import org.GLM.negoriator.domain.SupplierConstraintsSnapshot;
import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.IssueWeights;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferEvaluation;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationStrategy;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierArchetype;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/negotiations")
public class NegotiationController {

	private final NegotiationApplicationService negotiationApplicationService;

	public NegotiationController(
		NegotiationApplicationService negotiationApplicationService
	) {
		this.negotiationApplicationService = negotiationApplicationService;
	}

	@GetMapping("/config/defaults")
	public SessionDefaultsResponse getDefaults() {
		return SessionDefaultsResponse.from(
			NegotiationDefaults.defaultStrategy(),
			NegotiationDefaults.maxRounds(),
			NegotiationDefaults.riskOfWalkaway(),
			NegotiationDefaults.buyerProfile(),
			NegotiationDefaults.bounds(),
			NegotiationDefaults.supplierModel());
	}

	@PostMapping("/sessions")
	public ResponseEntity<NegotiationSessionResponse> startSession(
		@RequestBody(required = false) StartSessionRequest request
	) {
		NegotiationApplicationService.StartSessionCommand command = request == null
			? NegotiationDefaults.startSessionCommand()
			: request.toCommand();

		NegotiationSession session = negotiationApplicationService.startSession(command);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(NegotiationSessionResponse.from(negotiationApplicationService.getSession(session.getId())));
	}

	@GetMapping("/sessions/{sessionId}")
	public NegotiationSessionResponse getSession(
		@PathVariable UUID sessionId
	) {
		NegotiationSession session = negotiationApplicationService.getSession(sessionId);
		return NegotiationSessionResponse.from(session);
	}

	@PutMapping("/sessions/{sessionId}/settings")
	public NegotiationSessionResponse updateSessionSettings(
		@PathVariable UUID sessionId,
		@RequestBody UpdateSessionSettingsRequest request
	) {
		if (request == null) {
			throw new IllegalArgumentException("Session settings payload is required.");
		}

		NegotiationSession updatedSession = negotiationApplicationService.updateSessionSettings(
			sessionId,
			request.toCommand());

		return NegotiationSessionResponse.from(negotiationApplicationService.getSession(updatedSession.getId()));
	}

	@PostMapping("/sessions/{sessionId}/offers")
	public NegotiationSessionResponse submitSupplierOffer(
		@PathVariable UUID sessionId,
		@RequestBody SubmitSupplierOfferRequest request
	) {
		if (request == null) {
			throw new IllegalArgumentException("Supplier offer payload is required.");
		}

		NegotiationSession updatedSession = negotiationApplicationService.submitSupplierOffer(
			sessionId,
				request.toOfferVector(),
				request.toSupplierConstraints(),
				request.supplierMessage());

		return NegotiationSessionResponse.from(negotiationApplicationService.getSession(updatedSession.getId()));
	}

	public record SessionDefaultsResponse(
		String defaultStrategy,
		List<String> availableStrategies,
		List<StrategyDetailsResponse> strategyDetails,
		int maxRounds,
		BigDecimal riskOfWalkaway,
		BuyerProfileResponse buyerProfile,
		BoundsResponse bounds,
		SupplierModelResponse supplierModel
	) {
		static SessionDefaultsResponse from(
			NegotiationStrategy defaultStrategy,
			int maxRounds,
			BigDecimal riskOfWalkaway,
			BuyerProfile buyerProfile,
			NegotiationBounds bounds,
			SupplierModel supplierModel
		) {
			return new SessionDefaultsResponse(
				defaultStrategy.name(),
				Arrays.stream(NegotiationStrategy.values()).map(Enum::name).toList(),
				StrategyMetadata.all().stream().map(StrategyDetailsResponse::from).toList(),
				maxRounds,
				riskOfWalkaway,
				BuyerProfileResponse.from(buyerProfile),
				BoundsResponse.from(bounds),
				SupplierModelResponse.from(supplierModel));
		}
	}

	public record StartSessionRequest(
		String strategy,
		Integer maxRounds,
		BigDecimal riskOfWalkaway,
		BuyerProfileRequest buyerProfile,
		BoundsResponse bounds,
		SupplierModelRequest supplierModel
	) {
		NegotiationApplicationService.StartSessionCommand toCommand() {
			NegotiationStrategy strategyValue = strategy == null || strategy.isBlank()
				? NegotiationDefaults.defaultStrategy()
				: NegotiationStrategy.valueOf(strategy.trim().toUpperCase(Locale.ROOT));

			return new NegotiationApplicationService.StartSessionCommand(
				strategyValue,
				maxRounds == null ? NegotiationDefaults.maxRounds(strategyValue) : maxRounds,
				riskOfWalkaway == null ? NegotiationDefaults.riskOfWalkaway() : riskOfWalkaway,
				buyerProfile == null ? NegotiationDefaults.buyerProfile() : buyerProfile.toBuyerProfile(),
				bounds == null ? NegotiationDefaults.bounds() : bounds.toBounds(),
				supplierModel == null ? NegotiationDefaults.supplierModel() : supplierModel.toSupplierModel());
		}
	}

	public record UpdateSessionSettingsRequest(
		String strategy,
		Integer maxRounds,
		BigDecimal riskOfWalkaway,
		BuyerProfileRequest buyerProfile,
		BoundsResponse bounds
	) {
		NegotiationApplicationService.UpdateSessionSettingsCommand toCommand() {
			return new NegotiationApplicationService.UpdateSessionSettingsCommand(
				NegotiationStrategy.valueOf(require(strategy, "strategy").trim().toUpperCase(Locale.ROOT)),
				require(maxRounds, "maxRounds"),
				require(riskOfWalkaway, "riskOfWalkaway"),
				require(buyerProfile, "buyerProfile").toBuyerProfile(),
				require(bounds, "bounds").toBounds());
		}
	}

	public record SubmitSupplierOfferRequest(
		BigDecimal price,
		Integer paymentDays,
		Integer deliveryDays,
		Integer contractMonths,
		String supplierMessage,
		SupplierConstraintsRequest supplierConstraints
	) {
		OfferVector toOfferVector() {
			return new OfferVector(
				require(price, "price"),
				require(paymentDays, "paymentDays"),
				require(deliveryDays, "deliveryDays"),
				require(contractMonths, "contractMonths"));
		}

		SupplierConstraintsSnapshot toSupplierConstraints() {
			return supplierConstraints == null ? null : supplierConstraints.toSnapshot();
		}
	}

	public record NegotiationSessionResponse(
		UUID id,
		String sessionToken,
		String strategy,
		int currentRound,
		int maxRounds,
		BigDecimal riskOfWalkaway,
		String status,
		boolean closed,
		BuyerProfileResponse buyerProfile,
		BoundsResponse bounds,
		SupplierModelResponse supplierModel,
		List<NegotiationRoundResponse> rounds,
		List<StrategyHistoryResponse> strategyHistory,
		List<StrategyDetailsResponse> strategyDetails,
		List<ConversationEventResponse> conversation
	) {
			static NegotiationSessionResponse from(NegotiationSession session) {
			List<NegotiationRoundResponse> rounds = session.getDecisions().stream()
				.sorted(Comparator.comparing(NegotiationDecision::getRoundNumber)
					.thenComparing(NegotiationDecision::getDecidedAt))
				.map(decision -> NegotiationRoundResponse.from(session, decision))
				.toList();
			List<StrategyHistoryResponse> strategyHistory = session.getStrategyChanges().stream()
				.sorted(Comparator.comparing(NegotiationStrategyChange::getCreatedAt))
				.map(StrategyHistoryResponse::from)
				.toList();
			List<ConversationEventResponse> conversation = ConversationEventResponse.from(session, rounds, strategyHistory);

				return new NegotiationSessionResponse(
					session.getId(),
					session.getSessionToken(),
					session.getStrategy().name(),
					session.getCurrentRound(),
					session.getMaxRounds(),
					session.getRiskOfWalkaway(),
					session.getStatus().name(),
					session.isClosed(),
					BuyerProfileResponse.from(session.toBuyerProfile()),
					BoundsResponse.from(session.toNegotiationBounds()),
					SupplierModelResponse.from(session.initialSupplierModel()),
					rounds,
					strategyHistory,
					StrategyMetadata.all().stream().map(StrategyDetailsResponse::from).toList(),
					conversation);
			}
		}

	public record StrategyDetailsResponse(
		String name,
		String label,
		String summary,
		String concessionStyle,
		String boundaryStyle
	) {
		static StrategyDetailsResponse from(StrategyMetadata.StrategyDescriptor descriptor) {
			return new StrategyDetailsResponse(
				descriptor.name(),
				descriptor.label(),
				descriptor.summary(),
				descriptor.concessionStyle(),
				descriptor.boundaryStyle());
		}
	}

	public record StrategyHistoryResponse(
		int roundNumber,
		String previousStrategy,
		String nextStrategy,
		String trigger,
		String rationale,
		Instant at
	) {
		static StrategyHistoryResponse from(NegotiationStrategyChange change) {
			return new StrategyHistoryResponse(
				change.getRoundNumber(),
				change.getPreviousStrategy() == null ? null : change.getPreviousStrategy().name(),
				change.getNextStrategy().name(),
				change.getTrigger().name(),
				change.getRationale(),
				change.getCreatedAt());
		}
	}

	public record NegotiationRoundResponse(
		int roundNumber,
		OfferMessageResponse supplierOffer,
		SupplierParseDebugResponse supplierParseDebug,
		BuyerReplyResponse buyerReply
	) {
		static NegotiationRoundResponse from(NegotiationSession session, NegotiationDecision decision) {
			return new NegotiationRoundResponse(
				decision.getRoundNumber(),
				OfferMessageResponse.from(
					decision.getSupplierOffer().toOfferVector(),
					decision.getSupplierOffer().getCreatedAt(),
					decision.getSupplierOffer().getSupplierMessage()),
				SupplierParseDebugResponse.from(decision),
				BuyerReplyResponse.from(session, decision));
		}
	}

	public record SupplierParseDebugResponse(
		String supplierIntentType,
		String supplierIntentSource,
		Integer selectedBuyerOfferIndex,
		String supplierIntentDetails
	) {
		static SupplierParseDebugResponse from(NegotiationDecision decision) {
			return new SupplierParseDebugResponse(
				decision.getSupplierIntentType(),
				decision.getSupplierIntentSource(),
				decision.getSupplierSelectedBuyerOfferIndex(),
				decision.getSupplierIntentDetails());
		}
	}

	public record OfferMessageResponse(OfferTermsResponse terms, Instant at, String message) {
		static OfferMessageResponse from(OfferVector offerVector, Instant at, String message) {
			return new OfferMessageResponse(OfferTermsResponse.from(offerVector), at, message);
		}
	}

	public record BuyerReplyResponse(
		String decision,
		String resultingStatus,
		String reasonCode,
		String focusIssue,
		String strategyUsed,
		String strategyRationale,
		String explanation,
		Instant decidedAt,
		OfferTermsResponse counterOffer,
		List<OfferTermsResponse> counterOffers,
		EvaluationResponse evaluation
	) {
		static BuyerReplyResponse from(NegotiationSession session, NegotiationDecision decision) {
			List<OfferTermsResponse> counterOffers = session.getOffers().stream()
				.filter(offer -> offer.getRoundNumber().equals(decision.getRoundNumber()))
				.filter(offer -> offer.getParty() == NegotiationParty.BUYER)
				.sorted(Comparator.comparing(offer -> offer.getCreatedAt()))
				.map(offer -> OfferTermsResponse.from(offer.toOfferVector()))
				.toList();
			OfferTermsResponse visibleTerms = decision.getCounterOffer() != null
				? OfferTermsResponse.from(decision.getCounterOffer().toOfferVector())
				: decision.getDecision() == org.GLM.negoriator.domain.NegotiationDecisionType.ACCEPT
					? OfferTermsResponse.from(decision.getSupplierOffer().toOfferVector())
					: null;

			return new BuyerReplyResponse(
				decision.getDecision().name(),
				decision.getResultingStatus().name(),
				decision.getReasonCode() == null ? null : decision.getReasonCode().name(),
				decision.getFocusIssue() == null ? null : decision.getFocusIssue().name(),
				decision.getStrategyUsed() == null ? null : decision.getStrategyUsed().name(),
				decision.getStrategyRationale(),
				decision.getExplanation(),
				decision.getDecidedAt(),
				visibleTerms,
				counterOffers,
				EvaluationResponse.from(decision.toOfferEvaluation()));
		}
	}

	public record ConversationEventResponse(
		String eventType,
		String actor,
		String title,
		String message,
		Instant at,
		OfferTermsResponse terms,
		List<OfferTermsResponse> counterOffers,
		ConversationDebugResponse debug
	) {
		static List<ConversationEventResponse> from(
			NegotiationSession session,
			List<NegotiationRoundResponse> rounds,
			List<StrategyHistoryResponse> strategyHistory
		) {
			List<ConversationEventResponse> events = new java.util.ArrayList<>();
			java.util.Map<Integer, List<StrategyHistoryResponse>> nonInitialChangesByRound = strategyHistory.stream()
				.filter(change -> !"INITIAL_SELECTION".equals(change.trigger()))
				.collect(java.util.stream.Collectors.groupingBy(
					StrategyHistoryResponse::roundNumber,
					java.util.LinkedHashMap::new,
					java.util.stream.Collectors.toList()));
			java.util.Set<Integer> renderedChangeRounds = new java.util.HashSet<>();

			for (StrategyHistoryResponse change : strategyHistory) {
				if ("INITIAL_SELECTION".equals(change.trigger())) {
					events.add(new ConversationEventResponse(
						"STRATEGY_CHANGE",
						"system",
						"Session started",
						change.rationale(),
						change.at(),
						null,
						List.of(),
						new ConversationDebugResponse(
							change.nextStrategy(),
							change.rationale(),
							change.trigger(),
							null,
							null,
							null,
							List.of(),
							null,
							null,
							null,
							null)));
				}
			}

			events.add(new ConversationEventResponse(
				"BUYER_OPENING",
				"buyer",
				"Buyer opening request",
				session.getOpeningMessage(),
				session.getCreatedAt(),
				null,
				List.of(),
				null));

			for (NegotiationRoundResponse round : rounds) {
				appendStrategyChanges(
					events,
					nonInitialChangesByRound.get(round.roundNumber()));
				renderedChangeRounds.add(round.roundNumber());

				events.add(new ConversationEventResponse(
					"SUPPLIER_OFFER",
					"supplier",
					"Supplier proposal",
					round.supplierOffer().message() == null || round.supplierOffer().message().isBlank()
						? supplierMessage(round.supplierOffer().terms())
						: round.supplierOffer().message(),
					round.supplierOffer().at(),
					round.supplierOffer().terms(),
					List.of(),
					new ConversationDebugResponse(
						null,
						null,
						null,
						null,
						null,
						null,
						List.of(),
						round.supplierParseDebug().supplierIntentType(),
						round.supplierParseDebug().supplierIntentSource(),
						round.supplierParseDebug().selectedBuyerOfferIndex(),
						round.supplierParseDebug().supplierIntentDetails())));

				events.add(new ConversationEventResponse(
					"BUYER_REPLY",
					"buyer",
					"Buyer reply",
					round.buyerReply().explanation(),
					round.buyerReply().decidedAt(),
					round.buyerReply().counterOffer(),
					round.buyerReply().counterOffers(),
					new ConversationDebugResponse(
						round.buyerReply().strategyUsed(),
						round.buyerReply().strategyRationale(),
						null,
						round.buyerReply().reasonCode(),
						round.buyerReply().focusIssue(),
						round.buyerReply().evaluation(),
						counterOfferSummaries(round.buyerReply().counterOffers()),
						null,
						null,
						null,
						null)));
			}

			nonInitialChangesByRound.entrySet().stream()
				.filter(entry -> !renderedChangeRounds.contains(entry.getKey()))
				.forEach(entry -> appendStrategyChanges(events, entry.getValue()));

			return events;
		}

		private static void appendStrategyChanges(
			List<ConversationEventResponse> events,
			List<StrategyHistoryResponse> changes
		) {
			if (changes == null || changes.isEmpty()) {
				return;
			}

			for (StrategyHistoryResponse change : changes) {
				String title;
				if ("MANUAL_CONFIGURATION".equals(change.trigger())) {
					title = java.util.Objects.equals(change.previousStrategy(), change.nextStrategy())
						? "Settings updated"
						: "Strategy updated";
				} else {
					title = "Strategy switch";
				}

				events.add(new ConversationEventResponse(
					"STRATEGY_CHANGE",
					"system",
					title,
					change.rationale(),
					change.at(),
					null,
					List.of(),
					new ConversationDebugResponse(
						change.nextStrategy(),
						change.rationale(),
						change.trigger(),
						null,
						null,
						null,
						List.of(),
						null,
						null,
						null,
						null)));
			}
		}
	}

	public record ConversationDebugResponse(
		String strategy,
		String strategyRationale,
		String switchTrigger,
		String reasonCode,
		String focusIssue,
		EvaluationResponse evaluation,
		List<String> counterOfferSummary,
		String supplierIntentType,
		String supplierIntentSource,
		Integer supplierSelectedBuyerOfferIndex,
		String supplierIntentDetails
	) {
	}

	private static String supplierMessage(OfferTermsResponse terms) {
		return "We can offer price "
			+ terms.price()
			+ ", payment in "
			+ terms.paymentDays()
			+ " days, delivery in "
			+ terms.deliveryDays()
			+ " days, and a "
			+ terms.contractMonths()
			+ " month contract.";
	}

	private static List<String> counterOfferSummaries(List<OfferTermsResponse> counterOffers) {
		return counterOffers.stream()
			.map(offer -> "Price " + offer.price() + ", payment " + offer.paymentDays() + " days, delivery " + offer.deliveryDays() + " days, contract " + offer.contractMonths() + " months")
			.toList();
	}

	public record EvaluationResponse(
		BigDecimal buyerUtility,
		BigDecimal estimatedSupplierUtility,
		BigDecimal targetUtility,
		BigDecimal continuationValue,
		BigDecimal nashProduct
	) {
		static EvaluationResponse from(OfferEvaluation evaluation) {
			return new EvaluationResponse(
				evaluation.buyerUtility(),
				evaluation.estimatedSupplierUtility(),
				evaluation.targetUtility(),
				evaluation.continuationValue(),
				evaluation.nashProduct());
		}
	}

	public record OfferTermsResponse(
		BigDecimal price,
		int paymentDays,
		int deliveryDays,
		int contractMonths
	) {
		static OfferTermsResponse from(OfferVector offerVector) {
			return new OfferTermsResponse(
				offerVector.price(),
				offerVector.paymentDays(),
				offerVector.deliveryDays(),
				offerVector.contractMonths());
		}

		OfferVector toOfferVector() {
			return new OfferVector(price, paymentDays, deliveryDays, contractMonths);
		}
	}

	public record BoundsResponse(
		BigDecimal minPrice,
		BigDecimal maxPrice,
		int minPaymentDays,
		int maxPaymentDays,
		int minDeliveryDays,
		int maxDeliveryDays,
		int minContractMonths,
		int maxContractMonths
	) {
		static BoundsResponse from(NegotiationBounds bounds) {
			return new BoundsResponse(
				bounds.minPrice(),
				bounds.maxPrice(),
				bounds.minPaymentDays(),
				bounds.maxPaymentDays(),
				bounds.minDeliveryDays(),
				bounds.maxDeliveryDays(),
				bounds.minContractMonths(),
				bounds.maxContractMonths());
		}

		NegotiationBounds toBounds() {
			return new NegotiationBounds(
				minPrice,
				maxPrice,
				minPaymentDays,
				maxPaymentDays,
				minDeliveryDays,
				maxDeliveryDays,
				minContractMonths,
				maxContractMonths);
		}
	}

	public record BuyerProfileResponse(
		OfferTermsResponse idealOffer,
		OfferTermsResponse reservationOffer,
		IssueWeightsResponse weights,
		BigDecimal reservationUtility
	) {
		static BuyerProfileResponse from(BuyerProfile buyerProfile) {
			return new BuyerProfileResponse(
				OfferTermsResponse.from(buyerProfile.idealOffer()),
				OfferTermsResponse.from(buyerProfile.reservationOffer()),
				IssueWeightsResponse.from(buyerProfile.weights()),
				buyerProfile.reservationUtility());
		}
	}

	public record BuyerProfileRequest(
		OfferTermsResponse idealOffer,
		OfferTermsResponse reservationOffer,
		IssueWeightsRequest weights,
		BigDecimal reservationUtility
	) {
		BuyerProfile toBuyerProfile() {
			return new BuyerProfile(
				require(idealOffer, "buyerProfile.idealOffer").toOfferVector(),
				require(reservationOffer, "buyerProfile.reservationOffer").toOfferVector(),
				require(weights, "buyerProfile.weights").toIssueWeights(),
				require(reservationUtility, "buyerProfile.reservationUtility"));
		}
	}

	public record IssueWeightsResponse(
		BigDecimal price,
		BigDecimal paymentDays,
		BigDecimal deliveryDays,
		BigDecimal contractMonths
	) {
		static IssueWeightsResponse from(IssueWeights issueWeights) {
			return new IssueWeightsResponse(
				issueWeights.price(),
				issueWeights.paymentDays(),
				issueWeights.deliveryDays(),
				issueWeights.contractMonths());
		}
	}

	public record IssueWeightsRequest(
		BigDecimal price,
		BigDecimal paymentDays,
		BigDecimal deliveryDays,
		BigDecimal contractMonths
	) {
		IssueWeights toIssueWeights() {
			return new IssueWeights(
				require(price, "weights.price"),
				require(paymentDays, "weights.paymentDays"),
				require(deliveryDays, "weights.deliveryDays"),
				require(contractMonths, "weights.contractMonths"));
		}
	}

	public record SupplierModelRequest(
		Map<SupplierArchetype, BigDecimal> archetypeBeliefs,
		BigDecimal reservationUtility
	) {
		SupplierModel toSupplierModel() {
			return new SupplierModel(
				require(archetypeBeliefs, "supplierModel.archetypeBeliefs"),
				require(reservationUtility, "supplierModel.reservationUtility"));
		}
	}

	public record SupplierModelResponse(
		Map<SupplierArchetype, BigDecimal> archetypeBeliefs,
		BigDecimal reservationUtility
	) {
		static SupplierModelResponse from(SupplierModel supplierModel) {
			return new SupplierModelResponse(
				supplierModel.archetypeBeliefs(),
				supplierModel.reservationUtility());
		}
	}

	public record SupplierConstraintsRequest(
		BigDecimal priceFloor,
		Integer paymentDaysCeiling,
		Integer deliveryDaysFloor,
		Integer contractMonthsFloor
	) {
		SupplierConstraintsSnapshot toSnapshot() {
			return new SupplierConstraintsSnapshot(
				priceFloor,
				paymentDaysCeiling,
				deliveryDaysFloor,
				contractMonthsFloor);
		}
	}

	private static <T> T require(T value, String fieldName) {
		if (value == null) {
			throw new IllegalArgumentException(fieldName + " is required.");
		}

		return value;
	}
}
