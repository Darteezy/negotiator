package org.GLM.negoriator.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.GLM.negoriator.application.NegotiationApplicationService;
import org.GLM.negoriator.application.NegotiationDefaults;
import org.GLM.negoriator.domain.NegotiationDecision;
import org.GLM.negoriator.domain.NegotiationSession;
import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.IssueWeights;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferEvaluation;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierArchetype;
import org.GLM.negoriator.negotiation.NegotiationEngine.SupplierModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/negotiations")
public class NegotiationController {

	private final NegotiationApplicationService negotiationApplicationService;

	public NegotiationController(NegotiationApplicationService negotiationApplicationService) {
		this.negotiationApplicationService = negotiationApplicationService;
	}

	@GetMapping("/config/defaults")
	public SessionDefaultsResponse getDefaults() {
		return SessionDefaultsResponse.from(
			NegotiationDefaults.maxRounds(),
			NegotiationDefaults.riskOfWalkaway(),
			NegotiationDefaults.bounds());
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
	public NegotiationSessionResponse getSession(@PathVariable UUID sessionId) {
		return NegotiationSessionResponse.from(negotiationApplicationService.getSession(sessionId));
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
			request.toOfferVector());

		return NegotiationSessionResponse.from(negotiationApplicationService.getSession(updatedSession.getId()));
	}

	public record SessionDefaultsResponse(
		int maxRounds,
		BigDecimal riskOfWalkaway,
		BoundsResponse bounds
	) {
		static SessionDefaultsResponse from(int maxRounds, BigDecimal riskOfWalkaway, NegotiationBounds bounds) {
			return new SessionDefaultsResponse(maxRounds, riskOfWalkaway, BoundsResponse.from(bounds));
		}
	}

	public record StartSessionRequest(
		Integer maxRounds,
		BigDecimal riskOfWalkaway,
		BuyerProfileRequest buyerProfile,
		BoundsResponse bounds,
		SupplierModelRequest supplierModel
	) {
		NegotiationApplicationService.StartSessionCommand toCommand() {
			return new NegotiationApplicationService.StartSessionCommand(
				require(maxRounds, "maxRounds"),
				require(riskOfWalkaway, "riskOfWalkaway"),
				require(buyerProfile, "buyerProfile").toBuyerProfile(),
				require(bounds, "bounds").toBounds(),
				require(supplierModel, "supplierModel").toSupplierModel());
		}
	}

	public record SubmitSupplierOfferRequest(
		BigDecimal price,
		Integer paymentDays,
		Integer deliveryDays,
		Integer contractMonths
	) {
		OfferVector toOfferVector() {
			return new OfferVector(
				require(price, "price"),
				require(paymentDays, "paymentDays"),
				require(deliveryDays, "deliveryDays"),
				require(contractMonths, "contractMonths"));
		}
	}

	public record NegotiationSessionResponse(
		UUID id,
		int currentRound,
		int maxRounds,
		String status,
		boolean closed,
		BoundsResponse bounds,
		List<NegotiationRoundResponse> rounds
	) {
		static NegotiationSessionResponse from(NegotiationSession session) {
			List<NegotiationRoundResponse> rounds = session.getDecisions().stream()
				.sorted(Comparator.comparing(NegotiationDecision::getRoundNumber)
					.thenComparing(NegotiationDecision::getDecidedAt))
				.map(NegotiationRoundResponse::from)
				.toList();

			return new NegotiationSessionResponse(
				session.getId(),
				session.getCurrentRound(),
				session.getMaxRounds(),
				session.getStatus().name(),
				session.isClosed(),
				BoundsResponse.from(session.toNegotiationBounds()),
				rounds);
		}
	}

	public record NegotiationRoundResponse(
		int roundNumber,
		OfferMessageResponse supplierOffer,
		BuyerReplyResponse buyerReply
	) {
		static NegotiationRoundResponse from(NegotiationDecision decision) {
			return new NegotiationRoundResponse(
				decision.getRoundNumber(),
				OfferMessageResponse.from(decision.getSupplierOffer().toOfferVector(), decision.getSupplierOffer().getCreatedAt()),
				BuyerReplyResponse.from(decision));
		}
	}

	public record OfferMessageResponse(OfferTermsResponse terms, Instant at) {
		static OfferMessageResponse from(OfferVector offerVector, Instant at) {
			return new OfferMessageResponse(OfferTermsResponse.from(offerVector), at);
		}
	}

	public record BuyerReplyResponse(
		String decision,
		String resultingStatus,
		String explanation,
		Instant decidedAt,
		OfferTermsResponse counterOffer,
		EvaluationResponse evaluation
	) {
		static BuyerReplyResponse from(NegotiationDecision decision) {
			return new BuyerReplyResponse(
				decision.getDecision().name(),
				decision.getResultingStatus().name(),
				decision.getExplanation(),
				decision.getDecidedAt(),
				decision.getCounterOffer() == null ? null : OfferTermsResponse.from(decision.getCounterOffer().toOfferVector()),
				EvaluationResponse.from(decision.toOfferEvaluation()));
		}
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

	public record BuyerProfileRequest(
		OfferTermsResponse idealOffer,
		OfferTermsResponse reservationOffer,
		IssueWeightsRequest weights,
		BigDecimal reservationUtility,
		BigDecimal pricePenaltyAlpha,
		BigDecimal priceDeliveryInteractionLambda
	) {
		BuyerProfile toBuyerProfile() {
			return new BuyerProfile(
				require(idealOffer, "buyerProfile.idealOffer").toOfferVector(),
				require(reservationOffer, "buyerProfile.reservationOffer").toOfferVector(),
				require(weights, "buyerProfile.weights").toIssueWeights(),
				require(reservationUtility, "buyerProfile.reservationUtility"),
				require(pricePenaltyAlpha, "buyerProfile.pricePenaltyAlpha"),
				require(priceDeliveryInteractionLambda, "buyerProfile.priceDeliveryInteractionLambda"));
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
		BigDecimal updateSensitivity,
		BigDecimal reservationUtility
	) {
		SupplierModel toSupplierModel() {
			return new SupplierModel(
				require(archetypeBeliefs, "supplierModel.archetypeBeliefs"),
				require(updateSensitivity, "supplierModel.updateSensitivity"),
				require(reservationUtility, "supplierModel.reservationUtility"));
		}
	}

	private static <T> T require(T value, String fieldName) {
		if (value == null) {
			throw new IllegalArgumentException(fieldName + " is required.");
		}

		return value;
	}
}