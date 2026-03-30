# API

This project exposes a small REST API for session setup, live negotiation, AI-assisted parsing, and simulation.

Base URL in local Docker runs:

```text
http://localhost:8080
```

## Negotiation endpoints

### `GET /api/negotiations/config/defaults`

Returns the default session setup used by the frontend configuration page.

Use it for:

- default buyer profile values
- available strategies
- strategy labels and summaries
- default bounds and supplier model values

Example:

```bash
curl http://localhost:8080/api/negotiations/config/defaults
```

Response highlights:

- `defaultStrategy`
- `availableStrategies`
- `strategyDetails`
- `maxRounds`
- `buyerProfile`
- `bounds`
- `supplierModel`

### `POST /api/negotiations/sessions`

Creates a new negotiation session.

If you send no body, backend defaults are used. The frontend normally sends a full payload.

Example:

```bash
curl -X POST http://localhost:8080/api/negotiations/sessions \
  -H 'Content-Type: application/json' \
  -d '{
    "strategy": "BASELINE",
    "maxRounds": 8,
    "riskOfWalkaway": 0.15,
    "buyerProfile": {
      "idealOffer": {
        "price": 90,
        "paymentDays": 60,
        "deliveryDays": 7,
        "contractMonths": 6
      },
      "reservationOffer": {
        "price": 120,
        "paymentDays": 30,
        "deliveryDays": 30,
        "contractMonths": 24
      },
      "weights": {
        "price": 0.45,
        "paymentDays": 0.20,
        "deliveryDays": 0.20,
        "contractMonths": 0.15
      },
      "reservationUtility": 0.55
    }
  }'
```

Response highlights:

- `id`
- `strategy`
- `currentRound`
- `status`
- `buyerProfile`
- `conversation`
- `strategyDetails`

### `GET /api/negotiations/sessions/{sessionId}`

Returns the full session state.

Use it for:

- restoring the session view
- reading round history
- reading conversation events
- inspecting strategy history

Example:

```bash
curl http://localhost:8080/api/negotiations/sessions/SESSION_ID
```

Important response sections:

- `rounds`
- each round may include `supplierParseDebug` with supplier intent metadata
- `conversation`
- `strategyHistory`
- `closed`
- `status`

### `PUT /api/negotiations/sessions/{sessionId}/settings`

Updates the live session settings.

This is how the frontend applies a manual strategy change or edits buyer limits, weights, bounds, or round settings during a negotiation.

Example:

```bash
curl -X PUT http://localhost:8080/api/negotiations/sessions/SESSION_ID/settings \
  -H 'Content-Type: application/json' \
  -d '{
    "strategy": "BOULWARE",
    "maxRounds": 10,
    "riskOfWalkaway": 0.15,
    "buyerProfile": {
      "idealOffer": {
        "price": 90,
        "paymentDays": 60,
        "deliveryDays": 7,
        "contractMonths": 6
      },
      "reservationOffer": {
        "price": 120,
        "paymentDays": 30,
        "deliveryDays": 30,
        "contractMonths": 24
      },
      "weights": {
        "price": 0.45,
        "paymentDays": 0.20,
        "deliveryDays": 0.20,
        "contractMonths": 0.15
      },
      "reservationUtility": 0.55
    },
    "bounds": {
      "minPrice": 70,
      "maxPrice": 160,
      "minPaymentDays": 0,
      "maxPaymentDays": 120,
      "minDeliveryDays": 1,
      "maxDeliveryDays": 90,
      "minContractMonths": 1,
      "maxContractMonths": 36
    }
  }'
```

### `POST /api/negotiations/sessions/{sessionId}/offers`

Submits the supplier's latest offer to the buyer engine.

The frontend usually calls this after parsing a free-text supplier message into concrete terms.

Example:

```bash
curl -X POST http://localhost:8080/api/negotiations/sessions/SESSION_ID/offers \
  -H 'Content-Type: application/json' \
  -d '{
    "price": 118,
    "paymentDays": 30,
    "deliveryDays": 21,
    "contractMonths": 12,
    "supplierMessage": "We can do 118 if payment stays at 30 days and delivery is 21 days.",
    "supplierConstraints": {
      "priceFloor": 118,
      "paymentDaysCeiling": 30,
      "deliveryDaysFloor": 21,
      "contractMonthsFloor": 12
    }
  }'
```

Response highlights:

- `status`
- `currentRound`
- `rounds` with buyer reply details and `supplierParseDebug`
- `conversation` with rendered supplier and buyer events
- supplier conversation `debug` includes `supplierIntentType`, `supplierIntentSource`, `supplierSelectedBuyerOfferIndex`, and `supplierIntentDetails`
- buyer `reasonCode`, `focusIssue`, `evaluation`, and any `counterOffers`

The supplier parse debug object uses the same field names in both `rounds[].supplierParseDebug` and `conversation[].debug`, including `supplierSelectedBuyerOfferIndex`.

Supported `supplierIntentSource` values:

- `DETERMINISTIC`
- `AI_FALLBACK`

## AI parsing endpoint

### `POST /api/ai/parse-offer`

Parses a supplier message into structured terms.

This endpoint uses the single active provider configured for the backend.

Supported provider families:

- `ollama`
- `openai-compatible`

`openai-compatible` covers OpenAI and OpenAI-compatible endpoints such as OpenRouter.

If you use Ollama:

- the Ollama server must already be running
- `AI_BASE_URL` must point to that server
- `AI_CHAT_MODEL` must exist in that Ollama instance

If you use `openai-compatible`:

- `AI_BASE_URL` should point to the provider root
- `AI_API_KEY` must match that provider
- `AI_CHAT_MODEL` must be a model name accepted by that provider

The backend also applies deterministic option-selection and fallback handling rules, so this is not just a raw model passthrough.

Internally the backend uses Spring AI for chat calls and structured output parsing, but the REST contract of this endpoint stays the same.

Supplier-message handling is layered:

- this endpoint extracts structured terms and hard constraints
- the negotiation submission flow then resolves supplier intent deterministically
- only unresolved `UNCLEAR` intent cases may trigger AI fallback classification
- if ambiguity remains, the backend asks for clarification instead of auto-closing the deal

Supported supplier intent types in the submission flow:

- `ACCEPT_ACTIVE_OFFER`
- `SELECT_COUNTER_OPTION`
- `PROPOSE_NEW_TERMS`
- `REJECT_OR_DECLINE`
- `UNCLEAR`

Example:

```bash
curl -X POST http://localhost:8080/api/ai/parse-offer \
  -H 'Content-Type: application/json' \
  -d '{
    "supplierMessage": "Option 2 works for us, but we cannot go below 115.",
    "referenceTerms": {
      "price": 120,
      "paymentDays": 30,
      "deliveryDays": 20,
      "contractMonths": 12
    },
    "counterOffers": [
      {
        "price": 112,
        "paymentDays": 30,
        "deliveryDays": 20,
        "contractMonths": 12
      },
      {
        "price": 115,
        "paymentDays": 45,
        "deliveryDays": 25,
        "contractMonths": 12
      }
    ]
  }'
```

Typical response:

```json
{
  "price": 115,
  "paymentDays": 45,
  "deliveryDays": 25,
  "contractMonths": 12,
  "supplierConstraints": {
    "priceFloor": 115,
    "paymentDaysCeiling": null,
    "deliveryDaysFloor": null,
    "contractMonthsFloor": null
  }
}
```

Important note:

- this endpoint returns structured terms, not the final supplier intent classification shown in session conversation debug
- supplier intent debug is attached to negotiation session responses after `POST /api/negotiations/sessions/{sessionId}/offers`

## Simulation endpoints

### `POST /api/simulations`

Runs one full backend-driven simulation.

Example:

```bash
curl -X POST http://localhost:8080/api/simulations \
  -H 'Content-Type: application/json' \
  -d '{
    "strategy": "MESO",
    "supplierPersonality": "Professional but firm.",
    "maxRounds": 8
  }'
```

### `GET /api/simulations/stream`

Streams simulation progress as server-sent events.

Example:

```bash
curl -N 'http://localhost:8080/api/simulations/stream?strategy=BASELINE&maxRounds=8'
```

### `POST /api/simulations/batch`

Runs a batch of simulations across supplier personalities.

Example:

```bash
curl -X POST http://localhost:8080/api/simulations/batch \
  -H 'Content-Type: application/json' \
  -d '{
    "strategy": "CONCEDER",
    "personalities": [
      "Professional but firm.",
      "Stubborn on price but flexible on other terms."
    ]
  }'
```

## Error handling

Common failure cases:

- invalid or missing payload fields return request errors
- unknown strategies fail enum parsing
- closed sessions reject further offer submissions
- unavailable AI providers make `/api/ai/parse-offer` fail
- too many simulation streams return `429 Too Many Requests`

When you call the API from the frontend, failed responses are surfaced as readable error messages where possible.
