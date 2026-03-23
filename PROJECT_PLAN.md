# Project Plan

## Goal

Build a buyer-side autonomous negotiation agent for the Pactum challenge, expose it through a human-usable negotiation interface, and evolve the backend from a single rule-based strategy into a strategy portfolio with future AI-assisted selection.

## Current Delivery Status

### Completed foundations

- Backend Spring Boot project with Maven and Docker Compose.
- Frontend Vite + React supplier console.
- Negotiation REST controller for session lifecycle and supplier offer submission.
- Persistence model for negotiation sessions, offers, and decisions.
- Rule-based buyer engine for accept, counter, and reject decisions.
- Configurable buyer goals and limits exposed in the main session setup flow.
- Utility scoring over price, payment terms, delivery time, and contract length.
- Round-aware concession logic.
- Tradeoff-aware counteroffer generation, including price giveback for stronger non-price terms.
- Automated tests for core engine and persistence flow.

### In progress conceptually

- Documentation cleanup and architecture clarification.
- Evolving the structured supplier UI into a more conversational negotiation experience.

### Not implemented yet

- Multiple runtime strategies.
- Strategy selection mechanism.
- AI-assisted strategy switching.
- AI-generated buyer phrasing in the main negotiation flow.
- Replay and analytics UI.

## Delivery Principles

- Keep current-state docs accurate to the code.
- Separate implemented behavior from design intent.
- Preserve reproducibility through automated tests.
- Add new strategy features behind explicit contracts so that they remain testable.

## Phased Roadmap

### Phase 1: Public negotiation API

Target outcome:

- Expose the existing application service through REST endpoints.

Deliverables:

- `POST /sessions` to create a session.
- `POST /sessions/{id}/offers` to submit a supplier offer.
- `GET /sessions/{id}` to fetch current state.
- `GET /sessions/{id}/history` to fetch negotiation history.
- Request and response DTOs with validation.
- API documentation for negotiation flows.

Reasoning:

- The core engine already exists, but the challenge needs a human-accessible interface layer.

### Phase 2: Frontend supplier experience

Target outcome:

- Provide a UI where a human supplier can negotiate with the buyer agent.

Deliverables:

- Session creation flow.
- Offer submission form.
- Negotiation timeline showing supplier offers, buyer responses, decision explanations, and current status.
- Basic result summary when the session ends.

Recommended first version:

- A simple web app optimized for clarity over visual complexity.

### Phase 3: Strategy portfolio

Target outcome:

- Move from one adaptive rule-based strategy to multiple selectable strategies.

Candidate strategies:

- Baseline adaptive utility-threshold strategy.
- Boulware strategy.
- Conceder strategy.
- Tit-for-Tat strategy.
- MESO-style multi-offer strategy.

Required architecture changes:

- Introduce a strategy interface.
- Add per-strategy configuration.
- Capture selected strategy in the session state.
- Persist strategy decisions and rationale.
- Add tests per strategy and for strategy selection.

### Phase 4: Strategy selection mechanism

Target outcome:

- Select or switch strategies dynamically based on negotiation context.

Possible selection inputs:

- current round
- concession velocity
- supplier-offer history
- estimated supplier archetype
- walkaway risk
- distance from reservation utility

Possible implementation approaches:

- Rule-based selector first.
- AI-assisted selector later.
- Hybrid selector where AI recommends but rules enforce safety and reservation constraints.

Guardrails:

- Reservation limits must remain hard constraints.
- Selection logic must be observable and explainable.
- The chosen strategy and switching reason must be persisted.

### Phase 5: AI-assisted negotiation enhancements

Target outcome:

- Use AI where it adds value without replacing deterministic safety constraints.

Recommended AI roles:

- infer likely supplier priorities from negotiation text or offer patterns
- recommend strategy switching
- generate human-readable explanations
- help propose diversified MESO options

Recommended non-AI responsibilities:

- final reservation enforcement
- utility calculation
- hard reject rules
- state transitions

### Phase 6: Analytics, replay, and evaluation

Target outcome:

- Make negotiation outcomes measurable and comparable across strategies.

Deliverables:

- session replay
- per-round evaluation timeline
- agreement quality metrics
- strategy comparison dashboard
- outcome export for offline analysis

## Acceptance Criteria By Milestone

### Backend API milestone

- A session can be created and negotiated end to end through HTTP.
- Closed sessions reject further offers.
- Decision explanations are returned to the client.

### Frontend milestone

- A supplier can complete a full negotiation flow without using test fixtures or direct backend calls.

### Strategy milestone

- At least three strategies are implemented behind one contract.
- Each strategy has deterministic unit tests.
- The selected strategy is visible in logs and persisted state.

### AI-selection milestone

- AI recommendations are bounded by rule-based safety constraints.
- Strategy-switch explanations are stored for auditability.

## Risks And Mitigations

### Risk: documentation drifts from implementation

Mitigation:

- Treat current behavior and planned features as separate sections in every major doc.

### Risk: AI makes unsafe or inconsistent concessions

Mitigation:

- Keep reservation limits and state transitions deterministic.
- Use AI for recommendation, not unchecked execution.

### Risk: strategy switching becomes opaque

Mitigation:

- Persist the chosen strategy, trigger signals, and explanation on each round.

## Related Documents

- [README.md](README.md)
- [docs/architecture.md](docs/architecture.md)
- [docs/negotiation-engine.md](docs/negotiation-engine.md)
- [TASK.md](TASK.md)
