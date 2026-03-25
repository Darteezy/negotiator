# Negotiation Engine

This project uses a rule-based buyer engine.

The buyer negotiates across four issues:

- price
- payment days
- delivery days
- contract months

The final deal decision is deterministic. AI can help parse supplier messages and draft buyer wording, but AI does not decide whether the buyer accepts, counters, or rejects.

## Core idea

In each round, the backend does three things:

1. Turn the supplier offer into a buyer utility score.
2. Compare that score with the buyer's current target for this round.
3. Decide whether to accept, counter, or reject.

That sounds simple, but the current engine also adds:

- hard reservation checks
- strategy-specific concession curves
- issue ranking for counteroffers
- MESO multiple-option replies
- supplier constraint handling
- historical consistency checks so the buyer does not contradict earlier concessions

## Main backend pieces

The core logic lives in these backend classes:

- `NegotiationEngine`
- `NegotiationEngineImpl`
- `BuyerUtilityCalculator`
- `DecisionMaker`
- `CounterOfferGenerator`
- `NegotiationApplicationService`
- `SupplierMessageIntentParser`
- `SupplierMessageIntentAiFallbackService`

## Session flow

### 1. Session start

The frontend creates a session from the configuration page.

That session stores:

- the active strategy
- max rounds
- risk of walkaway
- buyer ideal offer
- buyer reservation offer
- issue weights
- negotiation bounds
- supplier model snapshot

The backend also records the initial strategy selection in strategy history.

### 2. Supplier offer arrives

When the supplier submits a message from the frontend:

1. The frontend calls the AI parsing endpoint.
2. The backend turns the supplier message into structured terms.
3. The application service resolves supplier intent deterministically into one of the supported intent types.
4. If intent remains `UNCLEAR`, the backend may ask the AI provider for a structured fallback classification.
5. Any detected supplier hard constraints are merged into the session.
6. The negotiation engine evaluates the current supplier offer unless the backend instead requests clarification.
7. The application service stores the supplier offer, buyer reply, evaluation data, supplier parsing metadata, and conversation event.

Supported supplier intent types:

- `ACCEPT_ACTIVE_OFFER`
- `SELECT_COUNTER_OPTION`
- `PROPOSE_NEW_TERMS`
- `REJECT_OR_DECLINE`
- `UNCLEAR`

### 3. Session update

After the buyer reply is generated, the backend stores:

- the supplier offer
- any buyer counteroffers
- the decision type
- the resulting session status
- the evaluation metrics
- the active strategy and rationale
- the buyer-facing reply message
- the resolved supplier intent type and whether it came from deterministic parsing or AI fallback
- the selected buyer option index when the supplier referenced a numbered buyer option
- a short supplier intent details string used for debug visibility in the frontend

## Buyer profile

The buyer profile has four important parts.

### `idealOffer`

The buyer's preferred commercial outcome.

### `reservationOffer`

The buyer's hard limit on each issue.

Examples:

- price must not go above the reservation price
- payment days must not go below the reservation payment term
- delivery must not get slower than the reservation delivery term
- contract must not exceed the reservation contract length

### `weights`

How much each issue matters to the buyer.

The backend normalizes the weights before using them, so they do not need to sum exactly to `1.0` in the request.

### `reservationUtility`

The minimum overall utility floor the buyer can accept.

## Utility scoring

Every supplier offer is converted into a buyer utility score between `0.0000` and `1.0000`.

Meaning:

- `1.0000` is very strong for the buyer
- `0.0000` is the buyer's floor

The direction of preference is:

- lower price is better for the buyer
- longer payment terms are better for the buyer
- faster delivery is better for the buyer
- shorter contract length is better for the buyer

Each issue is normalized into a common scale, multiplied by its normalized weight, and combined into one buyer utility score.

## Strategy-driven target utility

The buyer does not expect the same quality level in every round.

The `DecisionMaker` calculates a target utility for the current round. That target changes by strategy.

### Current target curves

| Strategy      | Current target pattern                              |
| ------------- | --------------------------------------------------- |
| `BASELINE`    | `1.0 - progress`                                    |
| `MESO`        | `1.0 - progress^1.35`                               |
| `BOULWARE`    | `1.0 - progress^2.4`                                |
| `CONCEDER`    | `1.0 - sqrt(progress)`                              |
| `TIT_FOR_TAT` | baseline curve adjusted by recent supplier movement |

That means:

- `BOULWARE` stays stricter longer
- `CONCEDER` softens faster
- `BASELINE` stays in the middle
- `MESO` uses its own curve but mainly stands out through multiple options
- `TIT_FOR_TAT` rewards supplier concessions and becomes firmer when the supplier stalls

The target utility is never allowed to drop below the buyer's configured reservation utility.

## Hard rejection floor

The engine also calculates a hard reject threshold by strategy.

Current thresholds:

- `BASELINE`: `-0.0500`
- `MESO`: `-0.0600`
- `BOULWARE`: `-0.0350`
- `CONCEDER`: `-0.0800`
- `TIT_FOR_TAT`: `-0.0650` after a real supplier concession, otherwise `-0.0400`

This threshold is separate from the reservation checks. It controls when an offer is simply too weak to keep discussing.

## Decision rules

After scoring the supplier offer, the engine follows this order:

1. Check whether the offer breaks buyer reservation limits.
2. Compute buyer utility.
3. Compute target utility and hard reject threshold.
4. Reject immediately if utility is below the hard reject threshold.
5. Accept if utility meets the current target.
6. Reject on the final round if utility is still below the target floor.
7. Otherwise counter.

There is one important adjustment:

- if the offer is outside reservation limits but still close enough and there is time left, the engine can convert what would have been an accept or reject into a counter instead

That is how the buyer stays practical near the edge instead of behaving too abruptly.

## Reservation checks

An offer is outside the buyer reservation range if any of these are true:

- price is above the buyer reservation price
- payment days are below the buyer reservation payment term
- delivery days are above the buyer reservation delivery term
- contract months are above the buyer reservation contract term

If the offer is too far outside those limits, the engine rejects immediately.

If the offer is only slightly outside and there is still time left, the engine can continue with a counter.

The amount of slack before that immediate rejection depends on strategy.

## Counteroffer generation

When the decision is `COUNTER`, the backend builds one or more buyer-safe offers.

### Step 1: rank the gaps

`CounterOfferGenerator` measures the weighted gap between the supplier offer and the buyer ideal for each issue.

The biggest weighted gap is usually the first candidate issue.

### Step 2: respect supplier constraints

If the supplier message or prior parsing established a hard constraint, the engine can block that issue from being selected.

Examples:

- a supplier price floor blocks further buyer pressure on price
- a payment-days ceiling blocks asking for even longer payment
- a delivery-days floor blocks asking for even faster delivery
- a contract-months floor blocks asking for an even shorter contract

### Step 3: avoid repeating a dead move

If the buyer pushed one issue in the previous round and the supplier ignored it, the engine may promote another issue if it is still close enough in importance.

### Step 4: move halfway toward the ideal

The selected issue moves toward the buyer ideal.

Current movement rule:

- decimal values move halfway toward the target and are rounded
- integer values move halfway toward the target and are rounded

### Step 5: rebalance price for trade-offs

If the supplier improved on non-price terms, the buyer can now give some price back while staying buyer-safe.

This is one of the most important current behaviors.

It means the buyer can do things like:

- accept a slightly higher price in exchange for longer payment terms
- accept a slightly higher price for faster delivery
- accept a slightly higher price for a shorter contract

That price giveback is bounded:

- it cannot cross the buyer reservation price
- it cannot make the counter worse than the supplier's current offer from the buyer's point of view
- it keeps a minimum improvement margin over the current supplier offer

### Step 6: keep historical consistency

The engine checks earlier rounds so the buyer does not become more aggressive against a supplier package that is already as good as or better than a previous one.

In practice this means:

- price cannot suddenly become harsher against a comparable package
- payment, delivery, and contract demands are capped by the buyer's own earlier concession frontier

### Step 7: clamp to supplier constraints after generation

The application service applies any active supplier constraints again after counteroffers are generated.

So the flow is two-phase:

1. the engine uses constraints while ranking issues
2. the application service clamps returned counteroffers again before storing them

## MESO behavior

`MESO` uses the same safety checks as the other strategies, but it can return up to three viable counteroffers in one round.

Those options come from the top-ranked issues, not from random variation.

The goal is to test supplier preferences without weakening the buyer position.

If several options survive the viability checks, the explanation text includes numbered option lines.

## Response metadata

Each buyer reply carries structured metadata that the frontend can use directly.

Important fields:

- `reasonCode`
- `focusIssue`
- `evaluation`
- `counterOffers`

Each supplier conversation event also carries parse debug metadata.

Important supplier debug fields:

- `supplierIntentType`
- `supplierIntentSource`
- `supplierSelectedBuyerOfferIndex`
- `supplierIntentDetails`

This metadata appears in round history and in supplier conversation events so the frontend can show how each supplier message was interpreted.

Current `supplierIntentSource` values:

- `DETERMINISTIC`
- `AI_FALLBACK`

Current reason codes:

- `TARGET_UTILITY_MET`
- `OUTSIDE_RESERVATION_LIMITS`
- `FINAL_ROUND_WITHIN_LIMITS`
- `BELOW_HARD_REJECT_THRESHOLD`
- `FINAL_ROUND_BELOW_RESERVATION`
- `COUNTER_TO_CLOSE_GAP`

Current focus issues:

- `PRICE`
- `PAYMENT_DAYS`
- `DELIVERY_DAYS`
- `CONTRACT_MONTHS`

## Evaluation metrics

The backend stores extra values for diagnostics and later analysis.

Current metrics:

1. `buyerUtility`
2. `estimatedSupplierUtility`
3. `targetUtility`
4. `continuationValue`
5. `nashProduct`

These metrics are useful for understanding the round, but they are not all direct decision drivers.

## Supplier model

The supplier model is still lightweight.

It currently helps estimate supplier utility using four archetype beliefs:

- margin focused
- cashflow focused
- operations focused
- stability focused

Today this is mainly diagnostic. It does not yet drive automatic strategy switching.

## AI role

AI supports the negotiation flow, but it does not control the decision.

Current AI role:

- parse supplier messages into structured terms
- detect hard supplier constraints from free text
- classify unresolved supplier intent through a narrow structured fallback
- generate supplier-facing buyer wording in a professional procurement tone

Current non-AI role:

- utility scoring
- reservation checks
- accept, counter, reject decisions
- counteroffer generation
- strategy selection

The supplier parsing flow is not pure model output.

Current supplier parsing order:

1. parse structured terms and constraints from the supplier message
2. classify supplier intent deterministically
3. if intent is still `UNCLEAR`, optionally run AI fallback for structured intent classification
4. if the message is still ambiguous, ask for clarification instead of assuming acceptance

Buyer wording is also constrained before it is sent out:

- the model is prompted to write like a real procurement professional, not like an internal negotiation engine
- the wording should read like a short business email note to the supplier
- internal strategy names, targets, utility language, reservation logic, and similar hidden reasoning must not appear in supplier-facing text
- if AI wording is unavailable, the backend falls back to human-readable supplier-facing text rather than engine explanation text

## Shared example

Default buyer setup:

- ideal: price `90`, payment `60`, delivery `7`, contract `6`
- reservation: price `120`, payment `30`, delivery `30`, contract `24`

Supplier message:

```text
We can do price 118, payment in 30 days, delivery in 21 days, and a 12 month contract.
```

What usually happens:

1. The offer is parsed into structured terms.
2. The offer is still inside buyer hard limits.
3. Buyer utility is calculated.
4. The offer is likely below the current round target.
5. The engine ranks the biggest gaps.
6. The buyer returns a counter rather than accepting.

Possible internal explanation shape:

```text
Countered because the offer is still below the current target. Buyer utility is lower than the target for this round. The counteroffer changes the most important remaining gap for the buyer.
```

That explanation is internal engine reasoning. It is useful for debugging and inspection, but it is not the style that should be sent to the supplier.

One realistic counter shape under the default buyer profile could be:

```text
Price 104.00, payment 30 days, delivery 14 days, contract 12 months
```

That is still buyer-safe, improves the biggest remaining gaps, and keeps the negotiation alive without conceding all the way to the supplier position.

If the same negotiation is run under `MESO`, the reply may instead contain several numbered options.

If the supplier later says something like:

```text
Option 2 works for us, but we cannot go below 115.
```

then the parsing flow can:

- select the referenced buyer option
- record a supplier price floor of `115`
- prevent future counters from asking for a lower price than that floor

That changes the next countering step. Price may stop being the active issue, and the buyer may shift the negotiation toward payment, delivery, or contract terms instead.

## Acceptance nuance

There is one important product behavior in the application service.

If the supplier exactly accepts the buyer's active offer from the previous round, the backend can finalize the deal as an acceptance.

If the supplier sends terms that the buyer is ready to accept but does not clearly accept the buyer's active option, the backend can return a counter that effectively says:

```text
Buyer is ready to close on these terms. Reply with accept to finalize the deal.
```

That keeps the close explicit instead of assuming agreement from ambiguous supplier wording.

If the supplier references a numbered or descriptive buyer option clearly enough, the backend records that resolution in session history and frontend conversation debug.

## Current limitations

The engine is stronger than the first version, but it is still intentionally narrow.

Current limits:

- strategy switching is manual, not automatic
- supplier modeling is still shallow
- counteroffers are rule-based, not globally optimized packages
- AI parsing still depends on provider quality plus backend fallback heuristics
- replay and analytics are still limited compared with the core negotiation flow
