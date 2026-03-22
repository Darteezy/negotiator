# Negotiator

Negotiator is a buyer-side autonomous negotiation application built for the Pactum technical challenge. The current codebase now includes a Spring Boot backend, a supplier-facing Vite + React frontend, a rule-based buyer agent, persistence for sessions and decisions, and test coverage for the scoring and decision loop.

## Challenge Alignment

The challenge in [TASK.md](TASK.md) asks for a buyer agent that can negotiate with a human supplier across multiple terms such as price, payment terms, delivery time, and contract length.

This repository already implements the core buyer-side negotiation engine around those terms:

- Weighted utility scoring across four negotiation issues.
- Offer evaluation using configurable buyer preferences, reservation limits, and round context.
- A decision loop that accepts, counters, or rejects supplier offers.
- Persistence of sessions, offers, decisions, evaluation metrics, and supplier-belief snapshots.

Current gaps against the full challenge scope:

- Multiple runtime-selectable strategies are planned but not implemented.
- The current frontend is structured-offer first, not natural-language AI chat yet.
- The Spring AI endpoint exists, but it is not part of the negotiation engine yet.

## Current State

### Implemented

- Spring Boot backend with JPA persistence and PostgreSQL support.
- Vite 8 + React 19 frontend for the human supplier experience.
- Negotiation REST API for session creation, retrieval, and supplier-offer submission.
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

- Strategy portfolio with runtime strategy selection.
- AI-assisted strategy switching and richer supplier modeling.
- AI-generated buyer phrasing layered on top of the structured decision contract.
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

The current architecture is frontend-plus-backend:

- Vite + React hosts the supplier-facing negotiation workspace.
- Spring Boot hosts the negotiation logic and REST API.
- PostgreSQL stores session state for reconstruction and later analysis.
- The application service orchestrates session start and supplier-offer submission.
- A separate AI endpoint exists for generic chat completion experiments, but it is not used by the negotiation engine.

More detail is in [docs/architecture.md](docs/architecture.md).

## Tech Stack

- Java 21
- Spring Boot 3.4
- React 19
- Vite 8
- Spring Web
- Spring Data JPA
- PostgreSQL
- H2 for tests
- Spring AI OpenAI starter
- Tailwind CSS 4
- shadcn-style component structure
- Docker Compose
- Maven Wrapper

## Getting Started

### Prerequisites

- Docker and Docker Compose
- Java 21 if running outside Docker
- An `.env` file based on `.env.example`

### Start the full stack with Docker

1. Create the local environment file:

```bash
cp .env.example .env
```

2. Build and start the containers:

```bash
docker compose up --build
```

3. Open the app:

- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080`
- PostgreSQL: `localhost:5432`

The Dockerized frontend serves the built React app and proxies `/api` traffic to the backend service inside Compose.

To stop the Docker stack:

```bash
docker compose down
```

### Start the frontend outside Docker

```bash
cd frontend
npm install
npm run dev
```

Frontend development server: `http://localhost:5173`

The Vite dev server proxies `/api` requests to `http://localhost:8080`.

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

Optional frontend override:

- `VITE_API_BASE_URL` if you do not want to use the Vite development proxy

The OpenAI key is required for the generic AI controller in [AIController](backend/src/main/java/org/GLM/negoriator/controller/AIController.java). It is not required for the negotiation engine logic itself.

## Repository Layout

```text
negotiator/
├── frontend/
│   ├── src/
│   └── package.json
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
