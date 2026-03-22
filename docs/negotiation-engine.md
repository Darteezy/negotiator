# Negotiation Engine

## Purpose

This document explains the implemented negotiation algorithm, the reasoning behind its current strategy, how to test it, and how planned future strategies should fit into the design.

The current engine is deterministic and rule-based. It is implemented in:

- [NegotiationEngine](../backend/src/main/java/org/GLM/negoriator/negotiation/NegotiationEngine.java)
- [NegotiationEngineImpl](../backend/src/main/java/org/GLM/negoriator/negotiation/NegotiationEngineImpl.java)
- [BuyerUtilityCalculator](../backend/src/main/java/org/GLM/negoriator/negotiation/BuyerUtilityCalculator.java)
- [DecisionMaker](../backend/src/main/java/org/GLM/negoriator/negotiation/DecisionMaker.java)
- [CounterOfferGenerator](../backend/src/main/java/org/GLM/negoriator/negotiation/CounterOfferGenerator.java)

## Implemented Strategy

### Summary

The current behavior is a single adaptive utility-threshold strategy:

1. Score the supplier offer from the buyer perspective.
2. Compare the score to a round-dependent target utility.
3. Enforce hard reservation constraints.
4. Accept if the offer is good enough.
5. Reject if the offer is too poor to continue.
6. Otherwise counter by improving one issue toward the buyer ideal.

This is the only implemented negotiation strategy today. The engine does not yet switch among named strategies at runtime.

### Why this strategy exists

For the current stage of the project, this approach is appropriate because it is:

- deterministic
- easy to test
- explainable round by round
- easy to persist and replay
- a good baseline for comparing future strategies

## Input Model

The engine works on four negotiation issues:

- price
- payment days
- delivery days
- contract months

These are represented by `OfferVector` in [NegotiationEngine](../backend/src/main/java/org/GLM/negoriator/negotiation/NegotiationEngine.java).

### Buyer profile

The buyer profile defines:

- `idealOffer`: the buyer's target values
- `reservationOffer`: the buyer's hard deal limits
- `weights`: relative importance of each issue
- `reservationUtility`: the minimum acceptable utility floor for acceptable deals

Two additional parameters exist in the record but are not used by the current implementation:

- `pricePenaltyAlpha`
- `priceDeliveryInteractionLambda`

That matters for documentation accuracy. They are part of the domain contract, but they do not affect current decisions.

### Negotiation bounds

Bounds are used to normalize issue values into comparable scores. Without them, price, days, and months would not be comparable in one utility function.

### Supplier model

The supplier model currently contributes to evaluation rather than decision selection.

It supplies:

- archetype belief weights
- update sensitivity
- supplier reservation utility

The engine computes an estimated supplier utility from these beliefs, but it does not yet infer new beliefs from offer history.

## Algorithm Walkthrough

### Step 1: Calculate buyer utility

The buyer utility is computed in [BuyerUtilityCalculator](../backend/src/main/java/org/GLM/negoriator/negotiation/BuyerUtilityCalculator.java).

Each issue is normalized to the range $[0, 1]$, where higher is better for the buyer.

#### Price score

Lower price is better for the buyer:

$$
priceScore = \frac{maxPrice - price}{maxPrice - minPrice}
$$

#### Payment score

Longer payment terms are better for the buyer:

$$
paymentScore = \frac{paymentDays - minPaymentDays}{maxPaymentDays - minPaymentDays}
$$

#### Delivery score

Faster delivery is better for the buyer:

$$
deliveryScore = \frac{maxDeliveryDays - deliveryDays}{maxDeliveryDays - minDeliveryDays}
$$

#### Contract score

Shorter contract length is better for the buyer in the current implementation:

$$
contractScore = \frac{maxContractMonths - contractMonths}{maxContractMonths - minContractMonths}
$$

#### Weighted utility

The final buyer utility is the weighted sum of the four normalized scores:

$$
U_b = priceScore \cdot w_p + paymentScore \cdot w_{pay} + deliveryScore \cdot w_d + contractScore \cdot w_c
$$

The result is clamped to $[0, 1]$.

Important implementation note:

- the code assumes the weights behave like normalized weights
- the code does not enforce that the four weights sum to `1.0`

In practice, they should sum to `1.0` to keep the utility scale interpretable.

### Step 2: Calculate target utility for the current round

The target utility is computed in [DecisionMaker](../backend/src/main/java/org/GLM/negoriator/negotiation/DecisionMaker.java).

The target starts above the reservation utility and decreases as rounds progress.

First compute progress:

$$
progress = clamp\left(\frac{round}{maxRounds}, 0, 1\right)
$$

Then compute a stretch amount above reservation utility:

$$
stretch = (1 - reservationUtility) \cdot 0.5
$$

Then compute the target:

$$
targetUtility = reservationUtility + stretch \cdot (1 - progress)
$$

Interpretation:

- early rounds demand stronger offers
- late rounds tolerate more concession
- once the round reaches or exceeds `maxRounds`, the target converges to reservation utility

### Step 3: Enforce reservation-limit rejection

Before the engine considers acceptance or countering, it checks whether the supplier offer violates the buyer reservation offer.

The offer is immediately rejected if any of these conditions hold:

- offered price is above buyer reservation price
- offered payment days are below buyer reservation payment days
- offered delivery days are above buyer reservation delivery days
- offered contract months are above buyer reservation contract months

This rule is implemented in [NegotiationEngineImpl](../backend/src/main/java/org/GLM/negoriator/negotiation/NegotiationEngineImpl.java).

This is one of the most important safety properties in the system because it acts as a hard boundary independent of other heuristics.

### Step 4: Decide accept, reject, or counter

The decision rules are:

1. Accept if `utility >= targetUtility`.
2. Reject if `utility < reservationUtility * 0.5`.
3. Reject if the last round has been reached and `utility < reservationUtility`.
4. Otherwise counter.

The hard reject threshold is:

$$
hardRejectThreshold = reservationUtility \cdot 0.5
$$

Interpretation:

- a good offer is accepted immediately
- an extremely poor offer is rejected immediately
- a borderline offer remains negotiable while rounds remain

### Step 5: Generate a counteroffer

Counteroffers are generated in [CounterOfferGenerator](../backend/src/main/java/org/GLM/negoriator/negotiation/CounterOfferGenerator.java).

The algorithm identifies the single issue that hurts the buyer most after weighting and normalization.

For each issue, it computes a weighted gap between the supplier offer and the buyer ideal.

Examples:

- price gap grows when the supplier price is above the buyer ideal price
- payment gap grows when offered payment days are below the buyer ideal payment days
- delivery gap grows when offered delivery time is slower than the buyer ideal
- contract gap grows when offered contract length is longer than the buyer ideal

The engine chooses the issue with the largest weighted normalized gap and moves only that issue toward the ideal.

#### Move rule

- decimal values move halfway toward target and are rounded to two decimals
- integer values move halfway toward target using integer division
- if halfway movement does not change the value, the algorithm forces at least one step
- the result is clamped to negotiation bounds

Why only one issue changes:

- it makes the concession easier to explain
- it exposes which issue the buyer cares about most
- it creates cleaner tests and future analytics

## Offer Evaluation Metrics

The engine stores more than the final decision. It also computes diagnostic metrics in [NegotiationEngineImpl](../backend/src/main/java/org/GLM/negoriator/negotiation/NegotiationEngineImpl.java).

### Estimated supplier utility

The engine approximates supplier utility from archetype beliefs:

- margin-focused belief weights price
- cashflow-focused belief weights shorter payment terms
- operations-focused belief weights longer delivery windows
- stability-focused belief weights longer contracts

The weighted result is divided by the total belief weight.

Important limitation:

- these beliefs are input assumptions, not learned beliefs
- the response currently returns the same belief map it received

### Continuation value

The continuation value is:

$$
continuationValue = targetUtility \cdot (1 - riskOfWalkaway)
$$

This metric is useful for future strategy selection and termination logic, but it does not currently drive the accept, reject, or counter decision directly.

### Nash product

The engine computes an approximate Nash product:

$$
buyerGain = max(buyerUtility - buyerReservationUtility, 0)
$$

$$
supplierGain = max(estimatedSupplierUtility - supplierReservationUtility, 0)
$$

$$
nashProduct = buyerGain \cdot supplierGain
$$

This metric is also diagnostic today. It is persisted for analysis but is not yet used to select the action.

## How The Algorithm Decides What Strategy To Use

### Current truth

Today it does not choose among multiple named strategies.

The implemented behavior is one strategy with adaptive parameters:

- round-aware target utility
- hard reservation enforcement
- hard reject floor
- single-issue countering

So the accurate answer for the current system is:

- there is no strategy selector yet
- the algorithm adapts behavior through thresholds and round progression
- the same decision pipeline runs for every negotiation

### Why that is still useful

Even without multiple strategies, the current engine already behaves differently across rounds:

- early rounds are stricter
- late rounds are more concessionary
- unacceptable offers are cut off immediately
- middling offers trigger focused counteroffers

That makes it a valid baseline strategy for future comparison.

## Future Strategy Portfolio

The interface file already contains TODO notes about future strategies in [NegotiationEngine](../backend/src/main/java/org/GLM/negoriator/negotiation/NegotiationEngine.java). The sections below are design guidance for future implementation, not current runtime behavior.

### 1. Baseline adaptive threshold strategy

Status:

- effectively implemented today

Behavior:

- concession happens through declining target utility
- counteroffers improve one issue at a time
- simple, explainable, and easy to test

Best use:

- default fallback strategy
- benchmark for all future strategies

### 2. Boulware strategy

Status:

- planned

Behavior:

- holds close to ideal terms for most of the negotiation
- concedes mainly near the deadline
- useful when the buyer has leverage or low urgency

Suggested implementation:

- replace the linear target-utility decline with a slower nonlinear curve
- keep reservation enforcement unchanged
- optionally make counteroffers smaller in early rounds

Potential trigger signals:

- low walkaway risk
- supplier already conceding
- strong buyer alternatives

Testing focus:

- target utility should remain high until late rounds
- acceptance should be rarer early than under the baseline strategy

### 3. Conceder strategy

Status:

- planned

Behavior:

- concedes earlier and faster
- useful when deal closure is more important than extracting the best terms

Suggested implementation:

- use a faster target-utility decay curve
- allow larger step sizes in counteroffers

Potential trigger signals:

- high walkaway risk
- late entry into negotiation
- high pressure to close

Testing focus:

- target utility should fall faster than the baseline strategy
- counteroffers should move more aggressively toward agreement

### 4. Tit-for-Tat strategy

Status:

- planned

Behavior:

- mirrors supplier concession patterns
- rewards cooperative movement and resists one-sided extraction

Suggested implementation:

- enrich history to record who made which offer and the direction of each concession
- compare supplier improvements between rounds
- generate buyer concessions proportional to supplier movement

Potential trigger signals:

- repeated multi-round negotiation
- stable supplier behavior with observable concession patterns

Testing focus:

- buyer concession magnitude should correlate with supplier concession magnitude
- stubborn supplier behavior should not trigger disproportionate buyer movement

### 5. MESO strategy

Status:

- planned

Behavior:

- return multiple equivalent simultaneous offers instead of one counteroffer
- each option preserves similar buyer utility while varying issue combinations
- useful for discovering supplier preferences and unlocking tradeoffs

Suggested implementation:

- use the utility function to generate multiple offers with roughly equal buyer utility
- diversify across payment, delivery, and contract structure rather than only price
- infer supplier preference signals from which option the supplier responds to or partially adopts

Potential trigger signals:

- negotiation stalls under single-offer countering
- uncertain supplier preferences
- enough issue flexibility to trade terms off against one another

Testing focus:

- generated MESO offers should remain within a narrow buyer-utility band
- offers should differ materially in issue composition
- no option may violate buyer reservation limits

## Future Strategy Selection Mechanism

### Desired behavior

The long-term design goal is to choose the right strategy for the current negotiation rather than hardcoding one path.

### Recommended architecture

Introduce two layers:

1. `NegotiationStrategy`
   - owns decision and counteroffer behavior
2. `StrategySelector`
   - selects the active strategy based on context and signals

Suggested selector inputs:

- round and rounds remaining
- buyer reservation distance
- concession trend across history
- supplier archetype beliefs
- walkaway risk
- stall detection
- prior strategy performance metrics

### Rule-based selector first

A safe first version would be deterministic:

- if walkaway risk is high, favor Conceder
- if supplier is conceding and time remains, favor Boulware
- if the negotiation stalls, try MESO
- if supplier behavior is reciprocating, try Tit-for-Tat
- otherwise fall back to the baseline adaptive strategy

### AI-assisted selector later

AI can be useful for recommendation, not unchecked control.

Good AI roles:

- infer likely supplier priorities from behavior or text
- recommend switching strategies
- explain the reason for switching in natural language
- help synthesize candidate MESO offers

Required guardrails:

- reservation limits remain hard coded and deterministic
- strategy switching must be logged and persisted
- the final selected action must still satisfy numeric safety checks

## How To Test The Current Engine Properly

### Test commands

Run the full backend suite:

```bash
cd backend
./mvnw test
```

### What is already covered

#### Utility scoring

[BuyerUtilityCalculatorTest](../backend/src/test/java/org/GLM/negoriator/negotiation/BuyerUtilityCalculatorTest.java) proves:

- price normalization preserves fractional precision
- best-bound offers score `1.0000`
- worst-bound offers score `0.0000`
- out-of-bounds offers are clamped into the valid range

#### Round-based target utility

[DecisionMakerTest](../backend/src/test/java/org/GLM/negoriator/negotiation/DecisionMakerTest.java) proves:

- target utility decreases as rounds advance
- target utility converges to reservation utility at or after the final round

#### Counteroffer logic

[CounterOfferGeneratorTest](../backend/src/test/java/org/GLM/negoriator/negotiation/CounterOfferGeneratorTest.java) proves:

- the engine chooses the issue with the largest weighted gap
- the counteroffer changes only the remaining non-ideal issue when others are already aligned

#### Full engine behavior

[NegotiationEngineTest](../backend/src/test/java/org/GLM/negoriator/negotiation/NegotiationEngineTest.java) proves:

- acceptance when utility meets target
- single-issue counteroffer generation
- immediate rejection on reservation-limit violations
- rejection of insufficient offers in the final round
- clamping of buyer-friendly out-of-bounds offers to full utility

#### Service and persistence loop

[NegotiationApplicationServiceTest](../backend/src/test/java/org/GLM/negoriator/application/NegotiationApplicationServiceTest.java) proves:

- session creation and supplier-offer submission work together
- supplier and buyer offers are persisted in one loop
- evaluation metrics and counteroffers are stored in the decision record

[NegotiationSessionRepositoryTest](../backend/src/test/java/org/GLM/negoriator/domain/NegotiationSessionRepositoryTest.java) proves:

- a full session can be reconstructed from persistence
- buyer profile, bounds, beliefs, offers, decisions, and evaluation metrics survive round trip through the repository

### What should be added next

For stronger confidence, add tests for:

- invalid weight configurations where weights do not sum to `1.0`
- zero-span bounds on each issue across the full engine path
- exact boundary behavior at reservation limits
- explanation-string stability if explanations become part of UI or API contracts
- supplier belief updating once it is implemented
- strategy selection and switching once multiple strategies exist
- MESO utility equivalence once multi-offer negotiation is implemented

### Recommended test pyramid for future work

- unit tests for each strategy's concession curve and counteroffer generation
- selector tests for strategy-choice rules
- integration tests for API endpoints
- persistence tests for replay and audit data
- end-to-end tests once the frontend exists

## Limitations Of The Current Implementation

These are important to document explicitly.

### 1. No public negotiation API yet

The negotiation engine is available through the application service and tests, not through a dedicated controller.

### 2. No frontend yet

The user-facing negotiation interface requested by the challenge is still planned.

### 3. No runtime strategy switching yet

The code contains design hints, but only one strategy is active today.

### 4. Supplier beliefs are not learned yet

Belief snapshots are persisted, and supplier utility is estimated from them, but there is no belief update algorithm in the current engine.

### 5. Some model parameters are unused

`pricePenaltyAlpha` and `priceDeliveryInteractionLambda` are present in the buyer profile contract but do not currently affect utility or decisions.

## Recommended Next Technical Steps

1. Add a negotiation REST controller over the existing application service.
2. Build a minimal frontend so the challenge can be exercised end to end by a human supplier.
3. Extract a strategy interface from the current rule-based engine.
4. Implement at least one alternative strategy such as Boulware or Conceder.
5. Add a rule-based selector before introducing AI-assisted switching.
6. Persist strategy choice and switching rationale for auditability.