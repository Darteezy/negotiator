# Testing

Testing is centered on deterministic behavior.

That keeps regressions reproducible, makes strategy comparisons easier to interpret, and makes failures easier to debug than a test strategy built mainly around AI-vs-AI runs.

Current scope:

- core negotiation behavior is covered by deterministic unit and integration tests
- strategy behavior is compared through a deterministic simulation matrix
- supplier parsing has dedicated parser, fallback, and transcript-style regression tests
- controller responses are checked through Spring MVC integration tests
- the current best path is deterministic scenario coverage first, not AI-vs-AI as the primary validation method

## Current testing structure

Most tests live under `backend/src/test/java/org/GLM/negoriator/` and fall into four groups:

- `negotiation/` for pure engine behavior and strategy evaluation
- `application/` for session orchestration, transcript flows, and parsing behavior
- `controller/` for API contract and response shape
- `ai/` for buyer message safety and fallback behavior around AI output

## What is covered today

### Negotiation engine tests

These tests validate the rule-based buyer logic directly.

Important suites:

- `NegotiationEngineImplTest`
- `CounterOfferGeneratorTest`
- `BuyerUtilityCalculatorTest`
- `StrategySettlementPolicyTest`
- `StrategySimulationMatrixTest`

Coverage includes:

- utility scoring behaves as expected
- counteroffers stay inside buyer-safe and supplier-constraint-aware boundaries
- settlement thresholds differ meaningfully by strategy
- MESO, Boulware, Conceder, Baseline, and Tit-for-Tat produce measurably different outcomes
- the engine does not regress on known negotiation edge cases

### Application-layer tests

These tests validate what happens when a real supplier message enters the negotiation flow.

Important suites:

- `NegotiationApplicationServiceTest`
- `SupplierMessageIntentParserTest`
- `SupplierMessageIntentAiFallbackServiceTest`
- `SessionConfigurationValidatorTest`

Coverage includes:

- supplier messages are classified into the right intent types
- MESO option selection stays open unless the supplier clearly accepts
- exact buyer-offer matches can close even when wording is vague
- ambiguous replies trigger clarification instead of accidental acceptance
- AI fallback only affects unresolved `UNCLEAR` intent cases
- buyer-acceptable supplier terms still require explicit supplier acceptance when needed

### Controller tests

These tests validate the REST contract returned to the frontend.

Main suite:

- `NegotiationControllerTest`

Coverage includes:

- session creation returns the expected initial conversation state
- settings updates affect strategy history and conversation events correctly
- supplier parse debug reaches both round history and conversation events in the API response

### AI-related tests

These tests protect against AI behavior leaking outside its intended role.

Main suite:

- `AiNegotiationMessageServiceTest`

Coverage includes:

- buyer-facing AI wording is constrained
- unsafe or meta-assistant style responses can be replaced by safer fallback behavior

## Strategy testing approach

The project now uses two complementary strategy-testing layers.

### 1. Direct settlement-policy tests

`StrategySettlementPolicyTest` checks small, precise expectations such as:

- opening threshold ordering across strategies
- Tit-for-Tat relaxing after meaningful supplier movement
- counteroffer clamping to strategy ceilings
- reservation utility overriding weaker strategy floors

These tests are fast and good for catching local regressions.

### 2. Deterministic simulation matrix

`StrategySimulationMatrixTest` runs a small scenario matrix across multiple supplier personas and all supported strategies.

Value:

- it provides outcome comparisons instead of isolated formulas
- it shows whether strategies actually behave differently end to end
- it makes tradeoffs visible in terms of close rate, buyer utility, supplier utility, and round count

Current matrix output focuses on:

- accept vs reject
- round of settlement or failure
- buyer utility
- estimated supplier utility
- final offer shape
- accepted-scenario averages per strategy

This is a much better signal than running the same single script many times.

## Why deterministic testing is the default

For this project, deterministic tests should be the primary evaluation method.

Reasons:

- they are repeatable
- failures are easier to debug
- regressions can be pinned to specific rules
- strategy comparisons stay meaningful across runs

AI-vs-AI simulations can still be useful later, but they are not the best primary test layer for algorithm work because:

- results are noisier
- model updates can change outcomes without code changes
- debugging becomes much harder
- it is harder to tell whether the algorithm or the model caused the behavior

## How to run tests

Run the full backend suite:

```bash
cd backend
./mvnw test
```

Run a focused suite:

```bash
cd backend
./mvnw -Dtest=NegotiationControllerTest test
```

Useful focused suites during parsing or strategy work:

```bash
./mvnw -Dtest=NegotiationApplicationServiceTest test
./mvnw -Dtest=SupplierMessageIntentParserTest test
./mvnw -Dtest=SupplierMessageIntentAiFallbackServiceTest test
./mvnw -Dtest=StrategySettlementPolicyTest test
./mvnw -Dtest=StrategySimulationMatrixTest test
```

## How to interpret the strategy matrix

The matrix is not meant to declare one strategy universally best.

Use it to answer more precise questions:

- which strategies settle earlier
- which strategies preserve more buyer utility when they do settle
- which personas produce rejection across all strategies
- whether strategy differences are still visible after an algorithm change

Healthy signs:

- different strategies produce different settlement timing or utilities
- cooperative scenarios close for more than one strategy
- hardliner scenarios do not collapse into identical outcomes for every strategy

Warning signs:

- every strategy behaves almost identically across the matrix
- all scenarios reject or all scenarios accept
- a strategy loses its characteristic timing profile after a code change

## Improvement from this review

One concrete gap was fixed during this review.

- controller integration coverage now verifies that supplier parse debug is returned in both `rounds[].supplierParseDebug` and `conversation[].debug`

That matters because supplier intent transparency is now part of the frontend workflow, not just internal persistence.

## Current testing gaps

The suite is materially stronger than before, but a few areas can still improve.

### 1. More API-contract examples around clarification and AI fallback

Current controller coverage now proves supplier parse debug exists, but the API layer would still benefit from explicit tests for:

- clarification responses in conversation history
- AI-fallback-resolved supplier debug in serialized responses

### 2. Scenario-library growth for the simulation matrix

The deterministic matrix is valuable, but it is still small.

Next useful additions would be:

- stronger supplier constraint scenarios
- more near-reservation edge cases
- scenarios where descriptive option references appear after MESO rounds

### 3. Frontend rendering coverage

The frontend build passes, but there are no dedicated frontend tests asserting that supplier parse debug fields render correctly.

This is not the highest risk area, but it is still a gap.

### 4. Snapshot-style golden transcript sets

The application tests already include transcript-style flows. A natural next step is a small set of named golden transcripts for:

- explicit acceptance
- option selection without acceptance
- descriptive option acceptance
- clarification trigger
- AI fallback resolution

That would make future parsing regressions even easier to understand.

## Recommended testing direction

If the goal is to improve the algorithm, the recommended order is:

1. add deterministic parser or engine regressions for a discovered issue
2. expand the scenario matrix when a strategy-level question appears
3. only use AI-vs-AI simulations as a secondary exploratory tool

That keeps the negotiation core explainable and easier to improve from observed results.
