# Pactum Technical Challenge — Autonomous Negotiation Agent

## About Pactum
Pactum builds AI-powered autonomous negotiation software. Our platform conducts human-like negotiations on behalf of large enterprises — think of an AI that can negotiate deals with thousands of suppliers simultaneously, finding outcomes that work for both sides.

## The Challenge
Build a negotiation agent that a human can negotiate with to reach a deal.

Imagine a simple scenario: a buyer wants to purchase goods from a supplier. They need to agree on several terms, for example:

- **Price** — the buyer wants to pay less, the supplier wants to earn more  
- **Payment terms** — how many days until payment is due (e.g., Net 30, Net 60, Net 90).  
  - The buyer prefers to pay later  
  - The supplier prefers to get paid sooner  
- **Delivery time** — the buyer wants faster delivery, the supplier prefers more flexibility  
- **Contract length** —  
  - The supplier may prefer a longer commitment for stability  
  - The buyer may want a shorter one for flexibility  

Each party has their own preferences and limits, and the interesting part is that terms can be traded off against each other — for example, a buyer might accept a higher price in exchange for better payment terms.

Your task is to:
- Build the **buyer side** as an automated negotiation agent  
- Provide a **UI** where a human can play the supplier and negotiate against it  

This mirrors what Pactum actually does — AI negotiates on behalf of buyers with suppliers.

## Requirements

### Must-haves
- **Negotiation engine**  
  A back-and-forth offer/counteroffer flow between the bot (buyer) and the human (supplier)

- **State management**  
  Track the negotiation state (e.g., pending, countered, accepted, rejected, expired)

- **Configurable bot**  
  The buyer bot should have configurable goals and limits (e.g., maximum acceptable price, preferred payment terms)

- **At least one negotiation strategy**  
  The bot should have logic for deciding when to accept, counter, or reject an offer

- **User interface**  
  A UI where a human can negotiate with the bot (chat-based, form-based, or any other approach)

### Nice-to-haves
- Multiple negotiation strategies that can be swapped or compared  
- Analytics or visualizations of negotiation outcomes  
  - Example: how close the deal is to each party's ideal terms  
- Negotiation history / replay  

## Approach

You can choose how to implement the negotiation strategy:

- **Rule-based**  
  Decision trees, scoring functions, thresholds  

- **LLM-powered**  
  Using any available LLM API as an agent  

- **Hybrid**  
  Combining rules with LLM capabilities  

All approaches are equally valid. The focus is on:
- Decision-making logic  
- Trade-offs behind your design choices  

## Hint

To evaluate and compare offers across different terms, consider using a **scoring (utility) function**:

- Assign weights to each term based on importance  
- Normalize values to a common scale  

This allows the agent to quantify trade-offs like:
> "lower price but slower delivery" vs "higher price but faster delivery"

You might also consider techniques like:

### MESO (Multiple Equivalent Simultaneous Offers)

Present several offers at once that are equally valuable to the agent but differ in structure:

- **Option A**: lower price, longer payment terms  
- **Option B**: higher price, shorter payment terms  

This helps:
- Reveal the other party’s preferences  
- Move the negotiation forward more efficiently  