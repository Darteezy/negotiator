### Negotiator

### Overview
Negotiator is a buyer-side automated negotiation agent with a supplier-facing UI. The system runs structured offer/counteroffer rounds, applies configurable strategies, and tracks outcomes across negotiation sessions.

### Core Features
- Configurable negotiation engine with strategy selection.
- Buyer agent goals and limits (budget, target price, fallback rules).
- Supplier UI for live negotiation sessions.
- Session history and outcome reporting.

### Tech Stack
- Backend - Java21 + Spring Boot
- Frontend - React
- Database - PostGreSQL
- Node.js
- Docker and Docker compose
- Backend: Java SpringBoot
- Frontend: React
- Database: PostgreSQL
- Docker
- API docs: Swagger / OpenAPi
- Build tools: Maven

### Hosting
Server

### Architecture
- Frontend (React) consumes backend REST APIs for sessions, offers, and configuration.
- Backend (Spring Boot) runs negotiation orchestration and strategy evaluation.
- Database (PostgreSQL) persists users, sessions, offers, and configuration data.
- Docker Compose runs local dependencies for development.

### Database Schema
- Users: id, role, created_at.
- Sessions: id, buyer_id, supplier_id, status, created_at, updated_at.
- Offers: id, session_id, party, price, terms, created_at.
- Strategies: id, name, parameters, active.

### API Endpoints
- POST /sessions: create a negotiation session.
- GET /sessions/{id}: fetch session state and current offer.
- POST /sessions/{id}/offers: submit an offer or counteroffer.
- GET /sessions/{id}/history: retrieve offer history.
- GET /strategies: list available strategies.
- PUT /strategies/{id}: update strategy parameters.

### Implementation Phases
1. Phase 0: Project Foundation. Confirm scope, key user flows, and MVP success criteria. Validate local dev setup with Docker Compose, backend, and frontend.
2. Phase 1: Backend Core. Define domain models for sessions, offers, and strategies. Implement REST API scaffolding and persistence. Add validation, error handling.
3. Phase 2: Negotiation Engine. Implement strategy interface and baseline strategies. Add negotiation loop with limits, timeouts, and stop conditions. Record session events and outcomes.
4. Phase 3: Frontend UI. Build session list and session detail views. Implement offer submission flow and live session updates. Add basic analytics or outcome summary view.
5. Phase 4: Integration & Quality. End-to-end flows across UI and API. Add logging, metrics, and API documentation. Expand automated tests for engine and API.
6. Phase 5: Deployment Readiness. Harden configuration and environment variables. Create production-ready Docker Compose or container pipeline. Document release checklist and operational notes.
