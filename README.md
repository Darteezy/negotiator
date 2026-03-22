# Negotiator

Negotiator is a buyer-side autonomous negotiation backend built for the Pactum technical challenge. The current codebase implements the negotiation domain model, a rule-based buyer agent, persistence for sessions and decisions, and test coverage for the scoring and decision loop.

The repository does not yet contain a supplier-facing frontend or a public negotiation REST API. Those are planned next steps and are documented separately as future work.

## Challenge Alignment

The challenge in [TASK.md](TASK.md) asks for a buyer agent that can negotiate with a human supplier across multiple terms such as price, payment terms, delivery time, and contract length.

This repository already implements the core buyer-side negotiation engine around those terms:

- Weighted utility scoring across four negotiation issues.
- Offer evaluation using configurable buyer preferences, reservation limits, and round context.
- A decision loop that accepts, counters, or rejects supplier offers.
- Persistence of sessions, offers, decisions, evaluation metrics, and supplier-belief snapshots.

Current gaps against the full challenge scope:

- No supplier UI is present in the workspace yet.
- No dedicated negotiation controller is exposed yet.
- Multiple runtime-selectable strategies are planned but not implemented.
- The Spring AI endpoint exists, but it is not part of the negotiation engine.

## Current State

### Implemented

- Spring Boot backend with JPA persistence and PostgreSQL support.
- Negotiation engine built from these components:
	- [BuyerUtilityCalculator](backend/src/main/java/org/GLM/negoriator/negotiation/BuyerUtilityCalculator.java)
	- [DecisionMaker](backend/src/main/java/org/GLM/negoriator/negotiation/DecisionMaker.java)
	- [CounterOfferGenerator](backend/src/main/java/org/GLM/negoriator/negotiation/CounterOfferGenerator.java)
	- [NegotiationEngineImpl](backend/src/main/java/org/GLM/negoriator/negotiation/NegotiationEngineImpl.java)
- Application service that starts sessions and records the full offer-decision loop:
	- [NegotiationApplicationService](backend/src/main/java/org/GLM/negoriator/application/NegotiationApplicationService.java)
- Persistence model for sessions, offers, decisions, buyer profiles, bounds, and supplier beliefs:
	- [NegotiationSession](backend/src/main/java/org/GLM/negoriator/domain/NegotiationSession.java)
- Automated tests for utility scoring, decision thresholds, counteroffers, engine orchestration, and repository persistence.

### Planned

- Supplier-facing frontend.
- Negotiation REST endpoints.
- Strategy portfolio with runtime strategy selection.
- AI-assisted strategy switching and richer supplier modeling.
- Better analytics, replay, and negotiation observability.

## Negotiation Strategy Today

The implemented engine uses one adaptive rule-based strategy.

At a high level:

1. Convert the supplier offer into a buyer utility score in the range `0.0000` to `1.0000`.
2. Compute the current target utility based on the buyer reservation utility and negotiation round.
3. Reject immediately if the offer violates buyer reservation limits.
4. Accept if the offer utility meets the current target.
5. Reject if the offer is far below the buyer floor, or if the last round is reached without meeting reservation utility.
6. Otherwise generate a counteroffer by improving the single issue that hurts the buyer most.

This behavior is implemented in [NegotiationEngineImpl](backend/src/main/java/org/GLM/negoriator/negotiation/NegotiationEngineImpl.java), [DecisionMaker](backend/src/main/java/org/GLM/negoriator/negotiation/DecisionMaker.java), and [CounterOfferGenerator](backend/src/main/java/org/GLM/negoriator/negotiation/CounterOfferGenerator.java).

For the full algorithm description, formulas, and test mapping, see [docs/negotiation-engine.md](docs/negotiation-engine.md).

## Architecture Summary

The current architecture is backend-first:

- Spring Boot hosts the application and negotiation logic.
- PostgreSQL stores session state for reconstruction and later analysis.
- The application service orchestrates session start and supplier-offer submission.
- A separate AI endpoint exists for generic chat completion experiments, but it is not used by the negotiation engine.

More detail is in [docs/architecture.md](docs/architecture.md).

## Tech Stack

- Java 21
- Spring Boot 3.4
- Spring Web
- Spring Data JPA
- PostgreSQL
- H2 for tests
- Spring AI OpenAI starter
- Docker Compose
- Maven Wrapper

## Getting Started

### Prerequisites

- Docker and Docker Compose
- Java 21 if running outside Docker
- An `.env` file based on `.env.example`

### Start PostgreSQL and the backend with Docker

```bash
cp .env.example .env
docker compose up --build
```

Services exposed by Compose:

- Backend: `http://localhost:8080`
- PostgreSQL: `localhost:5432`

### Run backend tests

```bash
cd backend
./mvnw test
```

## Environment Variables

The current backend expects these environment variables:

- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `POSTGRES_DB`
- `SPRING_DATASOURCE_URL` or Docker Compose-provided database URL wiring
- `OPENAI_API_KEY`

The OpenAI key is required for the generic AI controller in [AIController](backend/src/main/java/org/GLM/negoriator/controller/AIController.java). It is not required for the negotiation engine logic itself.

## Repository Layout

```text
negotiator/
├── backend/
│   ├── src/main/java/org/GLM/negoriator/
│   │   ├── application/
│   │   ├── controller/
│   │   ├── domain/
│   │   └── negotiation/
│   └── src/test/java/org/GLM/negoriator/
├── docs/
├── docker-compose.yml
├── PROJECT_PLAN.md
├── README.md
└── TASK.md
```

## Documentation Map

- [docs/architecture.md](docs/architecture.md): system structure, persistence flow, and current gaps.
- [docs/negotiation-engine.md](docs/negotiation-engine.md): algorithm, decision logic, test strategy, and future strategy design.
- [PROJECT_PLAN.md](PROJECT_PLAN.md): delivery roadmap and planned improvements.

