# Architecture

This project is a small full-stack negotiation system.

The frontend is supplier-facing. The backend acts as the buyer. PostgreSQL stores the session state. An external AI provider helps parse supplier messages and generate buyer wording, but it does not decide the negotiation outcome.

The current web frontend is a demo surface used to demonstrate and test the buyer workflow. It is not the only possible product channel. The same buyer engine could later be used behind supplier email workflows, chat interfaces, or other communication layers.

## High-level view

```text
Supplier user
  -> React frontend
  -> Spring Boot API
  -> negotiation application service
  -> rule-based negotiation engine
  -> PostgreSQL session storage

External AI provider
  -> supplier-message parsing
  -> buyer message generation
```

## Main parts

### Frontend

The frontend is a Vite and React app.

Its role today is practical rather than product-final: it lets a human supplier exercise the negotiation flow end to end and inspect how the buyer agent responds.

Main flow:

- `ConfigurationPage` creates the session
- `NegotiationPage` runs the live negotiation
- `negotiationApi` calls the backend endpoints

Runtime behavior:

- in local development, Vite proxies `/api` to `http://localhost:8080`
- in Docker, Nginx proxies `/api` to the backend container

What the frontend is responsible for:

- collecting buyer setup values
- sending supplier messages
- rendering the conversation timeline
- showing the supplier message immediately in the timeline while the backend reply is pending
- showing a temporary buyer loading response while the backend prepares the next reply
- applying live session setting changes
- displaying buyer explanations, utilities, and counteroffers
- displaying supplier-side parsing context for each supplier message, including resolved intent, source, selected buyer option, and parsing notes
- exposing strategy details through compact hint-tooltips in the session header and settings panel

Why it exists in the current project:

- demonstrate the negotiation engine in a usable way
- test the round-by-round negotiation flow with a human supplier
- expose buyer decisions and reasoning during development

What it does not do:

- it does not score offers
- it does not decide accept, counter, or reject
- it does not implement the negotiation strategy itself
- it should not be treated as the only long-term supplier channel

### Backend

The backend is a Spring Boot application.

The most important layers are:

- controllers expose the REST API
- `NegotiationApplicationService` orchestrates the session lifecycle
- `NegotiationEngineImpl` runs the decision logic
- domain entities store session, offers, decisions, and strategy history

What the backend is responsible for:

- creating sessions
- validating and updating configuration
- evaluating supplier offers
- generating buyer counteroffers
- storing every round and decision
- composing supplier-facing replies on behalf of the buyer

### Database

PostgreSQL stores the persistent session state.

Important stored records:

- session metadata
- buyer profile snapshot
- bounds snapshot
- supplier model snapshot
- supplier constraints snapshot
- supplier offers
- buyer counteroffers
- decisions and evaluation data
- strategy change history

The repository uses pessimistic write locking for live session updates so concurrent writes do not corrupt the round state.

### AI provider

The backend supports two provider modes:

- `ollama`
- `openai`

The provider is configured through environment variables.

AI is used for:

- structured term and constraint extraction from supplier messages
- supplier intent fallback only when deterministic parsing leaves the message unresolved
- buyer message generation in a supplier-facing, professional procurement tone

AI is not used for:

- utility scoring
- reservation checks
- strategy selection
- accept, counter, reject decisions

## Request flow

### Session creation

1. The frontend loads defaults from the backend.
2. The user configures buyer goals and strategy.
3. The frontend posts the session payload.
4. The backend validates the setup and stores a new session.
5. The backend records the initial strategy selection.

### Supplier message flow

1. The supplier types a free-text message in the negotiation view.
2. The frontend sends that message to the parsing endpoint.
3. The backend parses structured terms and constraints from the supplier message.
4. The frontend submits the normalized supplier offer to the negotiation session endpoint.
5. During offer submission, the backend resolves supplier intent deterministically into one of the supported intent types.
6. If intent is still `UNCLEAR`, the backend may call the configured AI provider for a structured fallback classification.
7. If ambiguity still remains, the backend requests clarification instead of guessing agreement.
8. Otherwise the backend runs the negotiation engine.
9. The backend stores the round result together with supplier parsing metadata and returns the updated session state.
10. The frontend shows the supplier message immediately, keeps a temporary buyer loading state visible, then re-renders the timeline with the latest buyer response and supplier-side parsing context.

Supported supplier intent types:

- `ACCEPT_ACTIVE_OFFER`
- `SELECT_COUNTER_OPTION`
- `PROPOSE_NEW_TERMS`
- `REJECT_OR_DECLINE`
- `UNCLEAR`

## Example path

Supplier message:

```text
We can do 118 if payment stays at 30 days and delivery is 21 days.
```

Typical system path:

1. Parsing resolves price `118`, payment `30`, delivery `21`, contract from the reference terms.
2. The backend scores the offer against the buyer profile.
3. The strategy sets the current target utility.
4. The buyer returns a counteroffer or MESO options.
5. The reply is stored in the session and shown in the UI.

## Session state and history

The backend keeps the negotiation state explicit.

Important status values:

- `PENDING`
- `COUNTERED`
- `ACCEPTED`
- `REJECTED`
- `EXPIRED`

The frontend does not reconstruct history on its own. It renders the conversation data returned by the backend.

## Strategy changes

Strategy switching is manual today.

That means:

- the user can start with one strategy
- the user can change strategy during the session
- each change is recorded in strategy history
- there is no automatic policy choosing a new strategy in the background

## Design boundary

The main design choice in this project is the split between deterministic negotiation logic and AI assistance.

Rule-based core:

- safer
- easier to test
- easier to explain
- easier to keep inside buyer limits

AI layer:

- better for reading supplier language
- better for producing natural buyer messages in a professional email-like tone
- only consulted for supplier intent when deterministic parsing remains unresolved
- not trusted with the final commercial decision

Product channel implication:

- the buyer engine should stay channel-agnostic
- the current frontend is one demo channel
- future work could wrap the same decision engine in supplier email or live chat communication without replacing the negotiation core

## Current limitations

- AI provider availability affects normal supplier-message parsing
- supplier preference modeling is still shallow
- strategy switching is manual only
- analytics and replay are lighter than the core negotiation loop
