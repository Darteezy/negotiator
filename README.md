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

- AI-assisted strategy switching is still planned, not implemented.
- The current frontend is structured-offer first, not natural-language AI chat yet.
- The optional AI endpoint exists for helper parsing and experiments, but it is not part of the negotiation engine yet.

That means the current MVP does work without any AI provider for the main negotiation flow. An external AI service is only needed for the optional parsing and chat helper endpoints.

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

- AI-assisted strategy switching and richer supplier modeling.
- AI-generated buyer phrasing layered on top of the structured decision contract.
- Better analytics, replay, and negotiation observability.

## Negotiation Strategy Today

The engine is still rule-based, but it now supports runtime strategy selection. The default is `MESO`, not `Boulware`.

At a high level:

1. Convert the supplier offer into a buyer utility score in the range `0.0000` to `1.0000`.
2. Compute the current target utility based on the buyer reservation utility and negotiation round.
3. Reject immediately if the offer violates buyer reservation limits.
4. Accept if the offer utility meets the current target.
5. Reject if the offer is far below the buyer floor, or if the last round is reached without meeting reservation utility.
6. Otherwise generate a counteroffer.

Current strategy behavior:

- `MESO` is the default. It returns up to three buyer-equivalent counteroffers so the supplier can choose among nearby trade-offs.
- `BOULWARE` keeps the buyer stricter for longer.
- `CONCEDER` relaxes faster.
- `BASELINE` and `TIT_FOR_TAT` currently use the baseline target curve, with richer tit-for-tat behavior still planned.

This behavior is implemented in [NegotiationEngineImpl](backend/src/main/java/org/GLM/negoriator/negotiation/NegotiationEngineImpl.java), [DecisionMaker](backend/src/main/java/org/GLM/negoriator/negotiation/DecisionMaker.java), and [CounterOfferGenerator](backend/src/main/java/org/GLM/negoriator/negotiation/CounterOfferGenerator.java).

For the full algorithm description, formulas, and test mapping, see [docs/negotiation-engine.md](docs/negotiation-engine.md).

## Architecture Summary

The current architecture is frontend-plus-backend:

- Vite + React hosts the supplier-facing negotiation workspace.
- Spring Boot hosts the negotiation logic and REST API.
- PostgreSQL stores session state for reconstruction and later analysis.
- The application service orchestrates session start and supplier-offer submission.
- A separate AI endpoint exists for generic chat completion and optional supplier-message parsing, but it is not used by the negotiation engine.
- The backend can call either a remote Ollama server or an OpenAI-compatible API.

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
- External AI provider via HTTP
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

The core negotiation MVP should be usable as soon as the frontend, backend, and database are up.

Docker Compose no longer runs Ollama. If you want AI-assisted parsing or the generic AI endpoint, point the backend at an external provider from `.env`.

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
sh mvnw test
```

## Environment Variables

The current backend expects these environment variables:

- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `POSTGRES_DB`
- `SPRING_DATASOURCE_URL` or Docker Compose-provided database URL wiring
- `AI_PROVIDER`
- `AI_BASE_URL`
- `AI_CHAT_MODEL`
- `AI_API_KEY` for OpenAI or OpenAI-compatible providers that require bearer auth

Optional frontend override:

- `VITE_API_BASE_URL` if you do not want to use the Vite development proxy

Recommended model defaults:

- `qwen2.5:7b-instruct`
- `qwen3.5:9b` if your machine has enough memory

Provider examples:

- Remote Ollama on another server:
  - `AI_PROVIDER=ollama`
  - `AI_BASE_URL=http://your-gpu-server:11434`
  - `AI_CHAT_MODEL=qwen3.5:9b`
- OpenAI:
  - `AI_PROVIDER=openai`
  - `AI_BASE_URL=https://api.openai.com/v1`
  - `AI_API_KEY=...`
  - `AI_CHAT_MODEL=gpt-4.1-mini`
- OpenAI-compatible server:
  - `AI_PROVIDER=openai`
  - `AI_BASE_URL=https://your-proxy-or-server/v1`
  - `AI_API_KEY=...` if required by that server
  - `AI_CHAT_MODEL=your-model-name`

The AI settings are only required for the optional AI controller in [backend/src/main/java/org/GLM/negoriator/controller/AIController.java](backend/src/main/java/org/GLM/negoriator/controller/AIController.java). They are not required for the negotiation engine logic itself.

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
