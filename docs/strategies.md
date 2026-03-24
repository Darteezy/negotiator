# Negotiation Strategies

This document explains the five buyer negotiation strategies in simple language.

The strategy is chosen when the session starts. It stays active until a user changes it manually in session settings. The system should not switch strategies automatically during the negotiation.

## Baseline

Baseline is the balanced default.

It makes steady concessions from round to round without acting especially tough or especially eager. It is a good choice when you want predictable behavior and a clear middle ground.

Near buyer limits, Baseline should keep negotiating while there is still a realistic path to a deal. It should reject only when the offer is clearly outside a workable range or the negotiation has run out of room.

Current code note: this remains the real default strategy for new sessions.

## Meso

Meso stands for offering more than one reasonable path at the same time.

Instead of pushing only one counteroffer, it should present a small set of buyer-safe options that are different in shape but similar in overall value for the buyer. This helps reveal what the supplier actually cares about.

Near buyer limits, Meso should try to keep the conversation alive with structured options before giving up. It should feel exploratory, not abrupt.

Current code note: the intent is multi-option negotiation, but the backend needs to stay the source of truth for those options.

## Boulware

Boulware is the firm strategy.

It holds close to the buyer target for longer and makes smaller concessions early. This fits cases where the buyer wants to protect value, avoid showing flexibility too soon, and force the supplier to move first.

Near buyer limits, Boulware should still counter if the deal is close enough to repair, but it should do that with tight movement and clear limits.

Current code note: Boulware needs to be visibly firmer than Baseline in both concession pace and message tone.

## Conceder

Conceder is the faster-closing strategy.

It softens earlier and accepts more movement over time when the goal is to improve the chance of reaching a deal. This is useful when speed matters more than squeezing out the last amount of value.

Near buyer limits, Conceder should be the most willing to keep negotiating as long as the offer is still recoverable inside the buyer range.

Current code note: Conceder should be meaningfully more flexible than Baseline, not just a renamed version of it.

## Tit for Tat

Tit for Tat is the reciprocal strategy.

It responds to supplier movement with buyer movement. If the supplier improves terms, the buyer should reward that progress. If the supplier stands still, the buyer should stay cautious.

Near buyer limits, Tit for Tat should prefer measured counters over immediate rejection when the supplier has shown real movement. It should feel responsive rather than automatic.

Current code note: Tit for Tat is a manual strategy choice. It should not depend on hidden automatic mode switching.
