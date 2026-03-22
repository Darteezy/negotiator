# Negotiation Engine

## Purpose

This document explains how the current buyer-side negotiation engine works in simple language.

The backend is still rule-based, but it now supports named strategies per session. The current default is `MESO`. The future plan is to keep the final deal decision rule-based, then add AI strategy advice and AI-generated formal messaging on top of that stable base.

The main implementation lives in:

- [NegotiationEngine](../backend/src/main/java/org/GLM/negoriator/negotiation/NegotiationEngine.java)
- [NegotiationEngineImpl](../backend/src/main/java/org/GLM/negoriator/negotiation/NegotiationEngineImpl.java)
- [BuyerUtilityCalculator](../backend/src/main/java/org/GLM/negoriator/negotiation/BuyerUtilityCalculator.java)
- [DecisionMaker](../backend/src/main/java/org/GLM/negoriator/negotiation/DecisionMaker.java)
- [CounterOfferGenerator](../backend/src/main/java/org/GLM/negoriator/negotiation/CounterOfferGenerator.java)

## Short Version

Today the buyer does this in every round:

1. Score the supplier offer from the buyer point of view.
2. Compare that score with the minimum score the buyer wants in the current round.
3. Reject immediately if the offer breaks any hard buyer limits.
4. Accept if the offer is strong enough.
5. Reject if the offer is too weak to continue.
6. Otherwise send a counteroffer.

Important clarification:

- the engine is not price-only
- the engine can change price, payment days, delivery days, and contract months
- most strategies still change one issue per counteroffer
- the `MESO` strategy can send multiple buyer-equivalent options in the same round
- with the default weights, price often wins first because it is the strongest buyer priority

## The Four Negotiation Issues

The engine negotiates on four terms:

- price
- payment days
- delivery days
- contract months

These four values are stored in `OfferVector`.

## What The Buyer Profile Means

Each session has a buyer profile with four important parts:

1. `idealOffer`
   - the best realistic deal the buyer wants
2. `reservationOffer`
   - the worst deal the buyer is still willing to accept on each issue
3. `weights`
   - how important each issue is to the buyer
4. `reservationUtility`
   - the minimum overall quality level the buyer can live with

Two fields still exist in the model but do not affect decisions yet:

- `pricePenaltyAlpha`
- `priceDeliveryInteractionLambda`

They should be treated as unused for now.

## How The Buyer Scores An Offer

The engine converts every offer into a buyer utility score between `0.0000` and `1.0000`.

Meaning:

- `1.0000` is excellent for the buyer
- `0.0000` is very bad for the buyer

The scoring rules are:

- lower price is better for the buyer
- longer payment terms are better for the buyer
- faster delivery is better for the buyer
- shorter contract length is better for the buyer in the current implementation

Each issue is normalized into the same scale, then multiplied by its buyer weight.

The backend now normalizes the weights before calculating utility. That means the utility scale stays interpretable even if the input weights do not already sum exactly to `1.0`.

## How The Buyer Becomes More Flexible Over Time

The buyer does not expect the same quality level in every round.

Early rounds:

- the buyer is stricter
- the target utility is higher

Later rounds:

- the buyer becomes more flexible
- the target utility moves closer to the reservation utility

So the current engine already has time-based concession behavior, and some strategies now change that concession curve.

Current strategy summary:

- `MESO`: default strategy, returns multiple equivalent counteroffers when possible
- `BOULWARE`: stays tougher for longer
- `CONCEDER`: relaxes faster
- `BASELINE`: uses the original linear target curve
- `TIT_FOR_TAT`: currently shares the baseline target curve until richer reciprocal history is added

## Hard Limits That Cause Immediate Rejection

Before the engine thinks about accept or counter, it checks hard buyer limits.

The offer is rejected immediately if any of these are true:

- price is above the buyer reservation price
- payment days are below the buyer reservation payment days
- delivery days are above the buyer reservation delivery days
- contract months are above the buyer reservation contract months

This is the strongest rule in the engine. It means some offers are non-negotiable regardless of the round.

## The Main Decision Rules

After hard-limit checks, the engine follows these rules:

1. Accept when buyer utility is at or above the current round target.
2. Reject when buyer utility is below the hard continuation floor.
3. Reject when the final round is reached and buyer utility is still below reservation utility.
4. Counter in all other cases.

In plain language:

- good offer: accept now
- very weak offer: reject now
- borderline offer: keep negotiating

## Why The Buyer Sometimes Seems To Change Only Price

This was a valid concern from testing the frontend.

What is actually happening:

- the current engine changes only one issue per counteroffer
- the default buyer weights still make price the most important issue
- so price often becomes the first thing the engine moves

What changed in the backend:

- weights are normalized before use
- the engine now exposes the exact reason code and focus issue in its response
- the counteroffer generator is less repetitive across rounds when the supplier ignores the same previous buyer counterissue and another meaningful issue is available

So the current engine is still simple, but it is now easier to inspect and less likely to get stuck repeating the same issue blindly.

## How Counteroffers Are Built

Most strategies still follow a one-issue counteroffer rule.

That means it does not try to change all four terms at once. Instead, it asks:

"Which single issue hurts the buyer most right now?"

Then it moves only that issue toward the buyer ideal.

Examples:

- if price is the biggest problem, the buyer changes price
- if delivery is the biggest problem, the buyer changes delivery days
- if payment is the biggest problem, the buyer changes payment days
- if contract length is the biggest problem, the buyer changes contract months

Why this rule exists:

- it keeps behavior explainable
- it makes each concession easy to test
- it shows which issue currently matters most to the buyer

`MESO` adds one important difference:

- instead of sending only one counteroffer, it can return up to three nearby options
- each option moves a different high-impact issue
- the buyer still keeps the decision explainable because each option is built from the same deterministic issue ranking logic

## New Response Metadata

Each buyer reply now includes more structured information:

- `reasonCode`
- `focusIssue`
- `explanation`

Current reason codes are:

- `TARGET_UTILITY_MET`
- `OUTSIDE_RESERVATION_LIMITS`
- `BELOW_HARD_REJECT_THRESHOLD`
- `FINAL_ROUND_BELOW_RESERVATION`
- `COUNTER_TO_CLOSE_GAP`

Current focus issues are:

- `PRICE`
- `PAYMENT_DAYS`
- `DELIVERY_DAYS`
- `CONTRACT_MONTHS`

This metadata is important because the frontend and future AI messaging should not guess why the engine made a decision.

## Evaluation Metrics Stored For Analysis

The backend also stores extra metrics for diagnostics:

1. `buyerUtility`
   - how good the offer is for the buyer
2. `targetUtility`
   - how good the offer needs to be in this round
3. `estimatedSupplierUtility`
   - rough estimate of supplier attractiveness based on current belief weights
4. `continuationValue`
   - expected value of continuing after adjusting for walkaway risk
5. `nashProduct`
   - rough fairness / joint-value indicator

These metrics are useful for analysis today. They are not yet the main rule drivers for action selection.

## Supplier Modeling Today

The backend keeps a supplier model with archetype beliefs such as:

- margin focused
- cashflow focused
- operations focused
- stability focused

Today this model is still mostly diagnostic.

What it does now:

- helps estimate supplier utility
- is stored with each decision

What it does not do yet:

- learn meaningfully from conversation history
- switch strategy automatically
- drive strategy selection

## Round Count Today

Round count is now strategy-aware.

Current defaults:

- `MESO`: `10` rounds
- `BOULWARE`: `10` rounds
- `CONCEDER`: `6` rounds
- `BASELINE`: `8` rounds
- `TIT_FOR_TAT`: `8` rounds

The session can still override these defaults when needed.

## Strategy Layer Today

The strategy set now exists at the session level:

1. Boulware
   - implemented as a slower concession curve
2. Conceder
   - implemented as a faster concession curve
3. Tit-for-Tat
   - selectable now, but still awaiting richer reciprocal behavior
4. MESO
   - implemented as the default strategy
   - generates multiple equivalent offers instead of just one

## Next Strategy Work

The next major backend evolution should keep the final deal decision rule-based, then deepen the strategy layer instead of replacing it.

Most important next steps:

- make `TIT_FOR_TAT` truly respond to supplier movement
- make the supplier model influence strategy recommendation
- let AI advise when to switch strategies
- improve MESO so it balances option quality more explicitly instead of only using ranked issue gaps

## Planned AI Role

The AI layer should not replace the deterministic decision core.

Recommended role:

1. AI strategy advisor
   - suggests which strategy to start with
   - suggests whether to switch strategy during negotiation
   - remains advisory only
2. AI message generator
   - converts structured backend decisions into formal supplier-facing chat or email text
   - should produce official business language
   - should never change the underlying deal decision

## Ollama Model Recommendation

For the current local AI path, the backend is now configured for Ollama.

Recommended default model:

- `qwen2.5:7b-instruct`

Why this is the best current default:

- good instruction following
- good enough tone control for formal business wording
- lighter local footprint than larger models
- suitable for advisory strategy prompts and supplier-facing message drafting

If you want a stronger but heavier local option later, `llama3.1:8b-instruct` is also a reasonable candidate.

## How To Test The Current Engine

Useful test coverage already exists in:

- [BuyerUtilityCalculatorTest](../backend/src/test/java/org/GLM/negoriator/negotiation/BuyerUtilityCalculatorTest.java)
- [DecisionMakerTest](../backend/src/test/java/org/GLM/negoriator/negotiation/DecisionMakerTest.java)
- [CounterOfferGeneratorTest](../backend/src/test/java/org/GLM/negoriator/negotiation/CounterOfferGeneratorTest.java)
- [NegotiationEngineTest](../backend/src/test/java/org/GLM/negoriator/negotiation/NegotiationEngineTest.java)
- [NegotiationApplicationServiceTest](../backend/src/test/java/org/GLM/negoriator/application/NegotiationApplicationServiceTest.java)
- [NegotiationControllerTest](../backend/src/test/java/org/GLM/negoriator/controller/NegotiationControllerTest.java)

Run all backend tests with:

```bash
cd backend
sh mvnw test
```

## Current Limitations

The engine is improved, but still intentionally simple.

Current limitations:

- AI is not yet part of the main negotiation loop
- `TIT_FOR_TAT` still behaves like the baseline target curve
- MESO still builds options from one-issue moves instead of full multi-issue optimization
- supplier belief updates are still shallow
- unused buyer profile parameters still exist

That is acceptable for the current phase, but it is also the reason the planned overhaul is necessary.
