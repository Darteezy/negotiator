# Supplier Message Parsing

Supplier messages do not go straight from raw text to a final deal decision.

The backend first extracts terms, then works out what kind of move the supplier is making.

That parsing layer matters because messages like these mean very different things:

- `We agree with option 1.`
- `Option 1 is closest for us.`
- `We can do 115 with 45-day payment.`
- `Please clarify the delivery point.`

Those messages can all mention the current negotiation context, but they should not all drive the same buyer behavior.

The parsing flow is layered.

- structured terms are extracted first
- supplier intent is classified next
- deterministic rules are authoritative
- AI fallback is only used for unresolved intent cases
- if ambiguity still remains, the backend asks for clarification instead of guessing

The main classes involved are:

- `SupplierMessageIntentParser`
- `SupplierMessageIntentAiFallbackService`
- `NegotiationApplicationService`

## Why parsing is separate from the deal decision

The supplier can send a message that means very different things even when the numeric terms look similar.

Examples:

- `We agree with option 1.` means acceptance of a buyer proposal
- `Option 1 is closest for us.` means the supplier is pointing at a buyer option without clearly closing
- `We can do 115 with 45-day payment.` is a new supplier counterproposal
- `Please clarify the delivery point.` is not a counter and not an acceptance

The buyer engine still owns the final commercial decision.

Parsing only answers the narrower question: what does the supplier appear to be doing in this message?

## End-to-end parsing flow

When the supplier sends a message in the frontend, the flow is:

1. The frontend calls `POST /api/ai/parse-offer`.
2. The backend extracts structured terms and hard constraints from the message.
3. The frontend submits the normalized offer to `POST /api/negotiations/sessions/{sessionId}/offers`.
4. The backend classifies supplier intent deterministically.
5. If deterministic classification returns `UNCLEAR` and there are active buyer offers, the backend may run AI fallback classification.
6. If the supplier message is still ambiguous, the backend asks for clarification instead of assuming acceptance.
7. The final interpretation is stored in session history and returned in response debug fields.

## Supported supplier intent types

The backend classifies supplier intent into one of these types.

### `ACCEPT_ACTIVE_OFFER`

Meaning:

- the supplier clearly accepted the buyer's current offer or one of the buyer's active options

Examples:

- `accept`
- `We agree with option 1.`
- `The first option works for us.`
- `Let's go with the faster delivery option.`

Typical effect:

- if the referenced terms match an active buyer offer and the offer is still buyer-acceptable, the round can close as `ACCEPTED`

### `SELECT_COUNTER_OPTION`

Meaning:

- the supplier referenced a buyer option, but did not clearly accept it

Examples:

- `option 2`
- `The faster delivery option is closest for us.`
- `That package is the nearest one.`

Typical effect:

- the backend keeps the negotiation open
- for MESO, this lets the supplier steer toward one buyer option without forcing an immediate close

### `PROPOSE_NEW_TERMS`

Meaning:

- the supplier proposed a fresh package instead of accepting the buyer's current position

Examples:

- `We can do 115 if payment stays at 30 days.`
- `If you accept, we can do price 102 and delivery 18 days.`
- `Price 102, payment 45, delivery 18, contract 12.`

Typical effect:

- the backend treats the message as a new supplier counterproposal and evaluates the structured terms directly

### `REJECT_OR_DECLINE`

Meaning:

- the supplier explicitly rejected or declined the buyer's current position

Examples:

- `We cannot accept these terms.`
- `This is not workable for us.`
- `No deal.`

Typical effect:

- the negotiation is treated as a decline signal rather than an acceptance or new package

### `UNCLEAR`

Meaning:

- the backend could not safely determine whether the supplier accepted, selected, declined, or proposed new terms

Examples:

- `Please clarify the contract point.`
- `What works for you on delivery?`
- `Can you explain the payment logic?`

Typical effect:

- AI fallback may be attempted if there are active buyer offers
- if the message still remains ambiguous, the backend asks for clarification

## Deterministic parsing rules

The deterministic parser looks for a few signal families.

### 1. Numbered buyer option references

Examples:

- `option 1`
- `offer 2`
- `the first option`
- `second package`

These help resolve `selectedBuyerOfferIndex` when the supplier points at a numbered buyer offer.

### 2. Descriptive buyer option references

The parser can also recognize references to an option by its shape instead of its number.

Examples:

- `the faster delivery option`
- `the lower price package`
- `the longer payment offer`

This matters most for MESO rounds where the supplier may describe an option instead of naming it by number.

### 3. Acceptance signals

The parser looks for direct acceptance wording first.

Examples:

- `accept`
- `agree`
- `confirmed`
- `we have a deal`

It also uses action-based acceptance wording when the message clearly points at a buyer option.

Examples:

- `go with option 2`
- `pick the first package`
- `the faster delivery option works for us`

### 4. Counterproposal signals

These signals stop the backend from misreading a fresh supplier package as agreement with the buyer.

Examples:

- `we can do`
- `if you accept`
- `final offer`
- explicit structured issue terms such as `price 102, payment 45, delivery 18`

### 5. Decline signals

Examples:

- `cannot accept`
- `will not proceed`
- `not acceptable`
- `no deal`

## Important behaviors

### Exact-offer match can still close even if the wording is unclear

If the supplier submits terms that exactly match one of the buyer's active offers, the backend can still treat that as acceptance even when the text itself stayed unclear.

Example context:

- buyer option 1: `P=102, Pay=50, Del=10, Ctr=12`
- supplier message: `These terms work.`
- supplier terms sent by the frontend exactly match buyer option 1

What happens:

- the round can close as acceptance
- debug details explain that the text was unclear but the terms matched an active buyer offer

### Buyer-acceptable terms do not auto-close without explicit supplier acceptance

If the supplier sends terms the buyer is willing to accept, but the supplier did not clearly accept the buyer's active offer, the backend keeps the close explicit.

Example:

- supplier terms are acceptable to the buyer
- supplier message is still a counterproposal or otherwise not an explicit acceptance

What happens:

- the backend returns a counter with the same terms
- the buyer message asks the supplier to confirm acceptance explicitly

Typical reply shape:

```text
Buyer is ready to close on these terms. Reply with accept to finalize the deal.
```

### Option selection is not the same as acceptance

For MESO and other multi-offer rounds, the backend distinguishes between selecting an option and accepting it.

Example:

- supplier message: `option 2`

What happens:

- classified as `SELECT_COUNTER_OPTION`
- negotiation remains open

Example:

- supplier message: `accept option 2`

What happens:

- classified as `ACCEPT_ACTIVE_OFFER`
- negotiation can close if the referenced option is valid and acceptable

## AI fallback behavior

AI fallback is intentionally narrow.

It is only attempted when:

- deterministic parsing returned `UNCLEAR`
- there are active buyer offers available to interpret against

AI fallback is not used to:

- replace deterministic parsing for obvious cases
- score utility
- decide accept, counter, or reject
- invent a commercial outcome from scratch

The AI fallback returns structured JSON intent, not free-form reasoning.

Possible `supplierIntentSource` values:

- `DETERMINISTIC`
- `AI_FALLBACK`

## Clarification behavior

If the supplier message remains unresolved after deterministic parsing and optional AI fallback, the backend asks for clarification instead of guessing.

This only happens when there are active buyer offers and the message is still genuinely ambiguous.

Typical clarification situations:

- the supplier references the current context vaguely but does not indicate accept or reject
- the supplier asks for clarification without making a commercial move
- the supplier seems to point at an offer family but not clearly enough to close

What happens:

- session status remains open
- buyer returns a clarification-style response
- active buyer offers may be repeated so the supplier can answer clearly

## Frontend and API debug fields

The parsing result is returned to the frontend so each supplier message can be inspected.

Main debug fields:

- `supplierIntentType`
- `supplierIntentSource`
- `supplierSelectedBuyerOfferIndex`
- `supplierIntentDetails`

These appear:

- in round history via `supplierParseDebug`
- in supplier conversation events via `conversation[].debug`

## Worked examples

### Example 1: explicit MESO acceptance

Context:

- buyer option 1: lower price
- buyer option 2: faster delivery

Supplier message:

```text
We agree with option 1.
```

Debug fields typically look like:

- `supplierIntentType = ACCEPT_ACTIVE_OFFER`
- `supplierIntentSource = DETERMINISTIC`
- `supplierSelectedBuyerOfferIndex = 1`
- `supplierIntentDetails = Supplier message was interpreted as accepting buyer option 1.`

### Example 2: option steering without close

Supplier message:

```text
Option 2 is closest for us.
```

Debug fields typically look like:

- `supplierIntentType = SELECT_COUNTER_OPTION`
- `supplierIntentSource = DETERMINISTIC`
- `supplierSelectedBuyerOfferIndex = 2`

Likely effect:

- negotiation stays open
- backend continues from the selected option context

### Example 3: descriptive option reference

Supplier message:

```text
Let's go with the faster delivery option.
```

Debug fields typically look like:

- `supplierIntentType = ACCEPT_ACTIVE_OFFER`
- `supplierIntentSource = DETERMINISTIC`
- `supplierSelectedBuyerOfferIndex = null`

Why the index may stay `null`:

- the supplier clearly accepted a descriptive buyer option
- the wording did not identify it by number

### Example 4: fresh supplier counterproposal

Supplier message:

```text
We can do 115 if payment stays at 30 days.
```

Debug fields typically look like:

- `supplierIntentType = PROPOSE_NEW_TERMS`
- `supplierIntentSource = DETERMINISTIC`
- `supplierIntentDetails = Supplier message was interpreted as a fresh supplier counterproposal.`

### Example 5: unresolved message leading to clarification

Supplier message:

```text
Can you clarify the contract point?
```

Debug fields typically look like if fallback still cannot resolve it:

- `supplierIntentType = UNCLEAR`
- `supplierIntentSource = DETERMINISTIC`
  or `AI_FALLBACK` if fallback was attempted and still did not resolve a closing interpretation
- `supplierIntentDetails = Supplier message remained unresolved ... so the backend requested clarification against the current buyer offer context.`

## What this parsing layer does not do

- it does not decide buyer utility
- it does not decide strategy
- it does not optimize counteroffers
- it does not replace the negotiation engine

It exists so the rest of the system can tell the difference between:

- supplier acceptance
- supplier option selection
- supplier counterproposal
- supplier decline
- unresolved ambiguity
