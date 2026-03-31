# Testing

Testing is centered on deterministic behavior.

That keeps regressions reproducible, makes strategy comparisons easier to interpret, and makes failures easier to debug than a test strategy built mainly around AI-vs-AI runs.

Scope:

- core negotiation behavior is covered by deterministic unit and integration tests
- strategy behavior is compared through a deterministic simulation matrix
- supplier parsing has dedicated parser, fallback, and transcript-style regression tests
- controller responses are checked through Spring MVC integration tests
- deterministic scenario coverage is the primary validation method, not AI-vs-AI runs

## Testing structure

Most tests live under `backend/src/test/java/org/GLM/negoriator/` and fall into four groups:

- `negotiation/` for pure engine behavior and strategy evaluation
- `application/` for session orchestration, transcript flows, and parsing behavior
- `controller/` for API contract and response shape
- `ai/` for buyer message safety and fallback behavior around AI output

## What is covered

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
- exact buyer-offer matches no longer auto-close when wording is vague
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

The project uses two complementary strategy-testing layers.

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

Matrix output focuses on:

- accept vs reject
- round of settlement or failure
- buyer utility
- estimated supplier utility
- final offer shape
- accepted-scenario averages per strategy

The matrix includes supplier-constraint-aware scenarios, so it can exercise cases where one issue is genuinely blocked and the buyer has to keep negotiating on the remaining terms.

This is a much better signal than running the same single script many times.

## What the matrix is simulating

The strategy matrix is a deterministic negotiation harness.

It does not call AI models and it does not replay a transcript from the frontend.

Instead, it runs the negotiation engine against a scripted supplier persona under a fixed scenario definition.

Each scenario defines:

- the supplier's opening offer
- the buyer profile used for that scenario
- the negotiation bounds
- an estimated supplier model used for evaluation metrics
- optional supplier constraints such as a price floor or delivery floor
- a deterministic supplier persona that decides how the supplier reacts to buyer counters

For each supported strategy, the test repeats the same scenario from round 1 and records the result.

That makes the comparison fair: the only thing changing between runs is the buyer strategy.

## How the scripted supplier works

The simulated supplier is not random.

Each persona has a fixed rule set that controls:

- which issues matter most to the supplier
- how good a buyer offer must be before the supplier accepts it
- how many early rounds the supplier will stall before making real movement
- how large the supplier's step sizes are in early rounds and late rounds

The persona always picks the buyer counteroffer that gives it the highest estimated supplier utility.

If that buyer offer is already good enough for the supplier at that round, the supplier accepts it and the scenario closes.

If not, the supplier produces a new counteroffer by moving from its current position toward the selected buyer offer using fixed deterministic step sizes.

If the scenario includes hard supplier constraints, those constraints are applied every round so the supplier never moves outside its declared floor or ceiling.

This means the matrix is not trying to model human language. It is trying to model repeatable negotiation pressure.

## How to read the matrix output

The report prints one row per scenario and strategy combination.

Main columns:

- `Scenario`: the deterministic supplier situation being tested
- `Strategy`: the buyer strategy used in that run
- `Decision`: whether that run ended in `ACCEPT` or `REJECT`
- `Round`: the round where the outcome happened
- `Buyer U`: buyer utility of the final supplier offer
- `Supp U`: estimated supplier utility of the final supplier offer
- `Price`: final price for quick scanning
- `Final Offer`: the full final offer in `P`, `Pay`, `Del`, `Ctr` form

The summary block at the end prints accepted averages by strategy.

Those rows answer three practical questions:

- when a strategy does settle, how much buyer utility does it usually preserve
- how late or early that strategy tends to settle
- which scenarios that strategy can close at all

## What the current scenarios mean

The current matrix has seven scenarios. They are intentionally small and opinionated rather than exhaustive.

### 1. Margin hardliner

This is the stubborn price-protection case.

Behavior:

- supplier opens at the buyer reservation edge on every issue
- supplier cares heavily about price
- supplier stalls for the early rounds before moving materially
- acceptance thresholds stay demanding even near the end

What it tests:

- whether firm strategies preserve more buyer utility under hard pressure
- whether softer strategies still fail gracefully instead of collapsing into irrational acceptance
- whether the matrix contains true rejection pressure

### 2. Price floor tradeoff

This is a blocked-price scenario.

Behavior:

- supplier starts from a moderately workable package
- supplier has an explicit price floor at `108.00`
- supplier still has room to move on payment, delivery, and contract
- buyer weights also emphasize price strongly, so the blocked issue matters

What it tests:

- whether the engine stops trying to push price below the known floor
- whether strategies can recover by negotiating other issues instead
- whether counteroffer ranking respects supplier constraints instead of repeating dead moves

### 3. Payment ceiling tradeoff

This is a blocked-payment scenario.

Behavior:

- supplier opens with payment already at `45` days
- supplier has a payment ceiling of `45`, so buyer requests for longer payment are blocked
- the supplier still has room to move on price, delivery, and contract

What it tests:

- whether the buyer pivots away from payment once that issue is closed
- whether the engine can still create workable counters without its preferred payment concession path
- whether strategies differ in how quickly they re-balance toward other issues

### 4. Delivery floor tradeoff

This is a blocked-delivery-speed scenario.

Behavior:

- supplier delivery cannot go faster than `16` days because of a delivery floor
- supplier cares a lot about delivery, so that blocked issue is commercially meaningful
- buyer weights also give delivery strong importance in this scenario

What it tests:

- whether the engine respects a hard operational delivery limit
- whether the buyer can still close on price, payment, or contract terms
- whether strategies remain differentiated when one major issue is removed from active bargaining

### 5. Late closer

This is the slow-progress scenario.

Behavior:

- supplier starts far from the buyer target but not fully hopeless
- supplier stalls for several rounds
- supplier moves more noticeably only later in the negotiation
- buyer reservation utility is slightly above zero, so the buyer still has standards

What it tests:

- whether patient strategies can benefit from waiting
- whether Conceder closes earlier than firmer strategies
- whether Boulware preserves more value if the supplier only becomes workable late

### 6. Near settlement

This is the easy-close scenario.

Behavior:

- supplier opens already near a buyer-acceptable deal
- supplier has no hard blocked issue
- supplier is willing to accept relatively good buyer offers early

What it tests:

- whether all strategies can still close a cooperative case
- whether strategy differences show up mostly in timing and utility rather than raw close/no-close outcomes
- whether aggressive strategies avoid over-negotiating a deal that is already close

### 7. Deadline settlement

This is the endgame-pressure scenario.

Behavior:

- supplier starts fairly close to the buyer target
- supplier acceptance becomes meaningfully easier near the last rounds
- buyer reservation utility is low but not zero, allowing endgame flexibility

What it tests:

- whether strategies respond differently to deadline pressure
- whether Conceder and Baseline close earlier than stricter strategies
- whether the matrix still shows timing spread when all strategies are operating near the settlement boundary

## What the scenario names do not mean

The scenario names are shorthand, not full business stories.

For example:

- `Margin hardliner` does not mean price is the only issue, only that price dominates the supplier posture
- `Price floor tradeoff` does not mean the supplier is otherwise cooperative on every issue, only that price is explicitly blocked
- `Near settlement` does not mean guaranteed acceptance, only that the opening package is already close enough for strategy differences to focus on timing and value retention

## What a good matrix result looks like

The matrix is useful when it shows clear separation.

In practice that usually means:

- `Conceder` closes more often or earlier in borderline scenarios
- `Boulware` closes later but preserves more buyer utility on accepted outcomes
- `Baseline` stays between the two
- `MESO` differs through option generation and can recover blocked-issue scenarios without behaving identically to single-counter strategies
- `Tit-for-Tat` reacts more strongly when the supplier persona begins to move

If those differences disappear, the matrix is telling you that the strategies may have converged in behavior even if their labels still differ.

## Why deterministic testing is the default

For this project, deterministic tests should be the primary evaluation method.

Reasons:

- they are repeatable
- failures are easier to debug
- regressions can be pinned to specific rules
- strategy comparisons stay meaningful across runs

AI-vs-AI simulations can be useful as a secondary tool, but they are not the best primary test layer for algorithm work because:

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
- blocked-issue scenarios still produce counters and settlements on other issues when a safe repair path exists

Warning signs:

- every strategy behaves almost identically across the matrix
- all scenarios reject or all scenarios accept
- a strategy loses its characteristic timing profile after a code change

## Testing gaps

The suite covers the core behavior, but a few areas can improve.

### 1. More API-contract examples around clarification and AI fallback

The API layer would benefit from explicit tests for:

- clarification responses in conversation history
- AI-fallback-resolved supplier debug in serialized responses

### 2. Scenario-library growth for the simulation matrix

The deterministic matrix is valuable, but it is small.

Useful additions would be:

- contract-floor scenarios where price and timing can still move
- more near-reservation edge cases
- scenarios where descriptive option references appear after MESO rounds

### 3. Frontend rendering coverage

The frontend build passes, but there are no dedicated frontend tests asserting that supplier parse debug fields render correctly.

This is not the highest risk area, but it is a gap.

### 4. Snapshot-style golden transcript sets

The application tests already include transcript-style flows. A useful addition is a small set of named golden transcripts for:

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
