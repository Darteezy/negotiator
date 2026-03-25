# Negotiation Strategies

The buyer supports five manual strategies.

They all use the same rule-based engine, the same buyer limits, and the same four negotiation issues:

- price
- payment days
- delivery days
- contract months

What changes from one strategy to another is mostly:

- how quickly the buyer relaxes over time
- how close an offer can be before the buyer still keeps talking
- how the buyer shapes counteroffers
- how the buyer message is framed

The strategy is chosen at session start and can be changed manually from session settings. The system does not switch strategies automatically today.

## Short comparison

| Strategy      | Main behavior                                        | Best fit                                                            |
| ------------- | ---------------------------------------------------- | ------------------------------------------------------------------- |
| `BASELINE`    | Balanced default with steady concessions             | General use when you want predictable behavior                      |
| `MESO`        | Returns several buyer-safe paths instead of only one | When you want to learn what the supplier actually values            |
| `BOULWARE`    | Holds firm longer and delays concessions             | When buyer value protection matters more than speed                 |
| `CONCEDER`    | Softens earlier and keeps momentum                   | When closing the deal matters more than extracting every last point |
| `TIT_FOR_TAT` | Responds to supplier movement more directly          | When you want reciprocity instead of a purely time-based pace       |

## Shared example setup

The examples below use the default buyer profile from the backend:

- Ideal offer: price `90`, payment `60`, delivery `7`, contract `6`
- Reservation offer: price `120`, payment `30`, delivery `30`, contract `24`
- Default weights: price `0.40`, payment `0.20`, delivery `0.25`, contract `0.15`

Supplier opening offer in the examples:

- price `118`
- payment `30`
- delivery `21`
- contract `12`

That offer is inside the buyer's hard limits, but still weak for the buyer. So the likely outcome is a counter, not an accept.

## Baseline

Baseline is the default starting strategy.

### What it does

- Uses a steady time-based concession path
- Keeps countering when there is still a realistic path to a buyer-safe deal
- Rejects only when the offer is clearly too weak or the session runs out of room

### What the supplier notices

- The buyer moves, but not too fast
- The buyer usually focuses on the biggest remaining gap
- The tone stays neutral and practical

### Counter style

Baseline usually returns one counteroffer.

It looks for the biggest weighted gap between the supplier offer and the buyer ideal. If the supplier ignored the buyer's last move on that issue, the engine may switch to a different issue if it is still close enough in importance.

### Example

Supplier says:

```text
We can offer price 118, payment in 30 days, delivery in 21 days, and a 12 month contract.
```

Typical Baseline response:

```text
We can move to price 104.00, payment in 30 days, delivery in 14 days, and a 12 month contract.
```

What this feels like in practice:

- not aggressive
- not eager
- trying to close the biggest gap first

## Meso

Meso is the option-based strategy.

### What it does

- Uses the same rule-based safety checks as every other strategy
- Returns up to three buyer-safe counteroffers when the engine can build several viable options
- Uses those options to test which trade-off the supplier prefers

### What the supplier notices

- The buyer gives more than one path forward
- The options are different in shape, not random variations
- The buyer is trying to reveal preferences without giving up control

### Counter style

Meso ranks the important issues, then tries to build separate viable counters from the highest-ranked issues.

If more than one option survives the viability checks, the buyer explanation includes numbered options.

### Example

Supplier says:

```text
We can offer price 118, payment in 30 days, delivery in 21 days, and a 12 month contract.
```

Possible Meso response:

```text
Option 1: price 104.00, payment 30 days, delivery 21 days, contract 12 months
Option 2: price 108.00, payment 45 days, delivery 21 days, contract 12 months
Option 3: price 109.00, payment 30 days, delivery 14 days, contract 12 months
```

What this feels like in practice:

- exploratory
- structured
- useful when the supplier's true priority is still unclear

## Boulware

Boulware is the firm strategy.

### What it does

- Concedes more slowly than the other strategies
- Keeps the target utility high for longer
- Uses the strictest near-boundary posture of the set

### What the supplier notices

- Early rounds feel tough
- The buyer does not reward weak movement very much
- The buyer's anchor stays visible for longer

### Counter style

Boulware still counters when there is a repair path, but the movement is tighter.

In the current implementation, this strategy also uses the smallest reservation slack before an immediate rejection becomes necessary.

### Example

Supplier says:

```text
We can offer price 118, payment in 30 days, delivery in 21 days, and a 12 month contract.
```

Typical Boulware response:

```text
We can move to price 101.00, payment in 30 days, delivery in 16 days, and a 12 month contract.
```

What this feels like in practice:

- firm
- controlled
- lower willingness to soften early

## Conceder

Conceder is the faster-closing strategy.

### What it does

- Lowers the target utility faster than the other strategies
- Keeps more near-boundary offers alive
- Accepts visible movement earlier when the deal still fits the buyer's limits

### What the supplier notices

- The buyer becomes workable sooner
- The gap closes faster in early and middle rounds
- The buyer is still bounded by reservation limits, but feels more flexible

### Counter style

Conceder still focuses on buyer-safe counters, but it is the most tolerant strategy when the current offer is close enough to rescue.

### Example

Supplier says:

```text
We can offer price 118, payment in 30 days, delivery in 21 days, and a 12 month contract.
```

Typical Conceder response:

```text
We can move to price 108.00, payment in 45 days, delivery in 16 days, and a 12 month contract.
```

What this feels like in practice:

- practical
- flexible
- optimized more for agreement rate than for holding the hardest line

## Tit-for-Tat

Tit-for-Tat is the reciprocal strategy.

### What it does

- Starts from a baseline-style time curve
- Then adjusts that posture based on recent supplier concessions
- Rewards movement and stays cautious when the supplier does not move

### How reciprocity works today

The backend looks at the most recent supplier move compared with the previous supplier offer.

It gives credit when the supplier improves on any of these terms:

- lower price
- longer payment days
- faster delivery
- shorter contract length

Those improvements reduce the target utility and make continued negotiation more likely. If the supplier is not moving, the strategy becomes firmer instead of rewarding stalling.

### What the supplier notices

- Cooperation gets a visible response
- Repeating the same position gets less reward
- The buyer feels more reactive than the other strategies

### Example

Round 1 supplier offer:

```text
Price 118, payment 30, delivery 21, contract 12.
```

Round 2 supplier offer:

```text
Price 115, payment 45, delivery 18, contract 12.
```

Typical Tit-for-Tat behavior:

```text
Round 1: buyer counters firmly.
Round 2: after the supplier improves price, payment, and delivery, the buyer responds with a more cooperative counter such as price 110.00, payment 45 days, delivery 14 days, contract 12 months.
```

What this feels like in practice:

- reciprocal
- conditional
- less predictable than Baseline, but still deterministic

## What all strategies still share

No strategy can override these rules:

- buyer reservation limits still apply
- the final decision is still rule-based
- counteroffers still need to be buyer-safe
- supplier constraints can block specific issue moves
- live strategy changes are manual only

## AI and strategy

AI does not choose the strategy today.

Current AI use around strategy is narrower:

- the strategy label and rationale influence buyer-facing message tone
- supplier messages are parsed with AI plus backend heuristics
- the actual accept, counter, and reject decision remains in backend rules

## Practical guidance

- Start with `BASELINE` when you want a dependable default
- Use `MESO` when supplier preferences are unclear and you want to test trade-offs
- Use `BOULWARE` when protecting buyer value matters most
- Use `CONCEDER` when speed to agreement matters more than squeezing the last bit of value
- Use `TIT_FOR_TAT` when you want buyer movement to depend more visibly on supplier movement
