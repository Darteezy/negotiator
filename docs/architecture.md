# Architecture

## Purpose

This document describes the architecture that is implemented in the repository today and separates it from planned extensions. It is intentionally code-backed so that the documentation stays aligned with the actual system.

## System Scope Today

The current repository is a backend-first negotiation system with persistence and tests.

Implemented components:

- Negotiation domain model and persistence.
- Rule-based buyer negotiation engine.
- Application service orchestration for session start and supplier-offer submission.
- PostgreSQL and local Docker Compose support.
- A standalone AI chat endpoint for experimentation.

Not implemented yet:

- Supplier-facing frontend.
- Public negotiation REST controller.
- Runtime strategy selector.
- AI-assisted strategy selection inside the negotiation loop.

## High-Level Component View

```text
Supplier Offer Input
	|
	v
NegotiationApplicationService
	|
	v
NegotiationEngineImpl
  |        |         |
  |        |         +--> CounterOfferGenerator
  |        +------------> DecisionMaker
  +---------------------> BuyerUtilityCalculator
	|
	v
NegotiationSession / NegotiationDecision / NegotiationOffer
	|
	v
PostgreSQL
```

Code references:

- [NegotiationApplicationService](../backend/src/main/java/org/GLM/negoriator/application/NegotiationApplicationService.java)
- [NegotiationEngineImpl](../backend/src/main/java/org/GLM/negoriator/negotiation/NegotiationEngineImpl.java)
- [NegotiationSession](../backend/src/main/java/org/GLM/negoriator/domain/NegotiationSession.java)

## Runtime Flow

### 1. Session creation

The application service creates a session with:

- current round
- max rounds
- risk of walkaway
- buyer profile snapshot
- negotiation bounds snapshot
- supplier model snapshot

This is implemented in [NegotiationApplicationService](../backend/src/main/java/org/GLM/negoriator/application/NegotiationApplicationService.java).

### 2. Supplier offer submission

When a supplier offer is submitted:

1. The session is loaded from the repository.
2. Closed sessions are rejected.
3. The supplier offer is persisted.
4. The current session state is transformed into a negotiation request.
5. The negotiation engine evaluates the offer.
6. A buyer counteroffer is persisted if one is returned.
7. The decision, evaluation metrics, supplier beliefs, and explanation are stored.
8. The session status and round are advanced.

This flow is implemented in [NegotiationApplicationService](../backend/src/main/java/org/GLM/negoriator/application/NegotiationApplicationService.java) and persisted via [NegotiationSession](../backend/src/main/java/org/GLM/negoriator/domain/NegotiationSession.java).

### 3. Engine decision loop

The engine uses a fixed rule-based pipeline:

1. Compute buyer utility.
2. Compute target utility for the current round.
3. Compute additional evaluation metrics.
4. Enforce reservation-limit rejection.
5. Decide accept, counter, or reject.
6. If countering, improve one issue toward the buyer ideal.

This logic is in [NegotiationEngineImpl](../backend/src/main/java/org/GLM/negoriator/negotiation/NegotiationEngineImpl.java).

## Core Domain Model

### Negotiation engine records

The negotiation contract is defined in [NegotiationEngine](../backend/src/main/java/org/GLM/negoriator/negotiation/NegotiationEngine.java):

- `OfferVector`: price, payment days, delivery days, contract months.
- `IssueWeights`: buyer importance weights for the four issues.
- `BuyerProfile`: buyer ideal offer, reservation offer, issue weights, and reservation utility.
- `SupplierModel`: supplier archetype beliefs, update sensitivity, and reservation utility.
- `NegotiationContext`: round data, state, walkaway risk, and history.
- `NegotiationBounds`: global numeric bounds used for normalization and clamping.
- `OfferEvaluation`: metrics computed during evaluation.
- `NegotiationResponse`: decision, next state, optional counteroffers, evaluation, and explanation.

### Persistence model

Persistent reconstruction is centered on [NegotiationSession](../backend/src/main/java/org/GLM/negoriator/domain/NegotiationSession.java):

- session metadata and status
- buyer profile snapshot
- bounds snapshot
- supplier model snapshot
- ordered offer history
- ordered decision history

Each decision stores:

- supplier offer
- optional buyer counteroffer
- evaluation metrics
- supplier belief snapshot used as the updated model for the next round
- textual explanation

See [NegotiationDecision](../backend/src/main/java/org/GLM/negoriator/domain/NegotiationDecision.java).

## State Management

Implemented states:

- `PENDING`
- `COUNTERED`
- `ACCEPTED`
- `REJECTED`
- `EXPIRED`

Current transition behavior:

- `ACCEPT` -> `ACCEPTED`
- `COUNTER` -> `COUNTERED`
- `REJECT` -> `REJECTED`
- Round number increments only when the outcome is `COUNTERED`

This is implemented in [NegotiationEngineImpl](../backend/src/main/java/org/GLM/negoriator/negotiation/NegotiationEngineImpl.java) and [NegotiationSession](../backend/src/main/java/org/GLM/negoriator/domain/NegotiationSession.java).

## Configuration Inputs

The buyer agent is configurable through the `BuyerProfile`, `NegotiationBounds`, and `SupplierModel` records.

### Buyer profile

Controls:

- ideal offer
- reservation offer
- per-issue weights
- reservation utility

Also present but not yet used in decision logic:

- `pricePenaltyAlpha`
- `priceDeliveryInteractionLambda`

### Negotiation bounds

Bounds define normalization ranges for every issue. They are essential because both buyer utility and estimated supplier utility are calculated from normalized issue scores.

### Supplier model

Current use:

- provides belief weights used to estimate supplier utility
- supplies a reservation utility for Nash product calculation

Current limitation:

- the beliefs are carried through and persisted, but the engine does not yet infer or update them from negotiation history. The response currently returns the same belief map it received.

## API Surface Today

The repository currently exposes only two controller entry points:

- [AIController](../backend/src/main/java/org/GLM/negoriator/controller/AIController.java): `GET /api/ai`
- [HelloController](../backend/src/main/java/org/GLM/negoriator/controller/HelloController.java): root mapping placeholder

There is no negotiation controller yet. The negotiation loop exists behind the application service and tests, not behind a public REST contract.

## Testing Architecture

The current tests validate three layers:

- Pure algorithm components.
- Full engine behavior.
- Persistence and application-service orchestration.

Key tests:

- [BuyerUtilityCalculatorTest](../backend/src/test/java/org/GLM/negoriator/negotiation/BuyerUtilityCalculatorTest.java)
- [DecisionMakerTest](../backend/src/test/java/org/GLM/negoriator/negotiation/DecisionMakerTest.java)
- [CounterOfferGeneratorTest](../backend/src/test/java/org/GLM/negoriator/negotiation/CounterOfferGeneratorTest.java)
- [NegotiationEngineTest](../backend/src/test/java/org/GLM/negoriator/negotiation/NegotiationEngineTest.java)
- [NegotiationApplicationServiceTest](../backend/src/test/java/org/GLM/negoriator/application/NegotiationApplicationServiceTest.java)
- [NegotiationSessionRepositoryTest](../backend/src/test/java/org/GLM/negoriator/domain/NegotiationSessionRepositoryTest.java)

Detailed test guidance is in [negotiation-engine.md](./negotiation-engine.md).

## Planned Architecture Evolution

The repository already hints at future strategy work in [NegotiationEngine](../backend/src/main/java/org/GLM/negoriator/negotiation/NegotiationEngine.java), where TODO notes mention a future strategy selection layer and named strategy concepts such as MESO, Boulware, Conceder, and Tit-for-Tat.

Planned additions:

- Frontend for supplier interaction.
- Negotiation REST API.
- Runtime strategy portfolio.
- Strategy selector that can use rules, heuristics, or AI assistance.
- Supplier model updates from history and response patterns.
- Negotiation replay and analytics views.

These are design directions, not current capabilities.
