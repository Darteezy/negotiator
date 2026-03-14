Pactum Technical Challenge — Autonomous Negotiation
Agent
About Pactum
Pactum builds AI-powered autonomous negotiation software. Our platform conducts human-like
negotiations on behalf of large enterprises — think of an AI that can negotiate deals with
thousands of suppliers simultaneously, finding outcomes that work for both sides.
The Challenge
Build a negotiation agent that a human can negotiate with to reach a deal.
Imagine a simple scenario: a buyer wants to purchase goods from a supplier. They need to
agree on several terms, for example:
Price — the buyer wants to pay less, the supplier wants to earn more
Payment terms — how many days until payment is due (e.g., Net 30, Net 60, Net 90). The
buyer prefers to pay later, the supplier prefers to get paid sooner.
Delivery time — the buyer wants faster delivery, the supplier prefers more flexibility
Contract length — the supplier may prefer a longer commitment for stability, while the
buyer may want a shorter one for flexibility
Each party has their own preferences and limits, and the interesting part is that terms can be
traded off against each other — for example, a buyer might accept a higher price in exchange
for better payment terms.
Your task is to build the buyer side as an automated negotiation agent and provide a UI
where a human can play the supplier and negotiate against it. This mirrors what Pactum
actually does — our AI negotiates on behalf of buyers with suppliers.
Requirements
Must-haves
Negotiation engine — a back-and-forth offer/counteroffer flow between the bot (buyer) and
the human (supplier)
State management — track the negotiation state (e.g., pending, countered, accepted,
rejected, expired)
Configurable bot — the buyer bot should have configurable goals and limits (e.g.,
maximum acceptable price, preferred payment terms)
At least one negotiation strategy — the bot should have logic for deciding when to
accept, counter, or reject an offer
User interface — a UI where a human can negotiate with the bot (chat-based, form-based,
or any other approach)
Nice-to-haves
Multiple negotiation strategies that can be swapped or compared
Analytics or visualizations of negotiation outcomes (e.g., how close to each party's ideal
terms did the deal land?)
Negotiation history / replay
Approach
You can choose how to implement the negotiation strategy:
Rule-based — decision trees, scoring functions, thresholds
LLM-powered — using any available LLM API as an agent
Hybrid — combining rules with LLM capabilities
All approaches are equally valid. We're especially interested in how you design the decision
making logic and the trade-offs behind your choice.
Hint: To evaluate and compare offers across different terms, consider using a scoring or utility
function — assign weights to each term based on importance and normalize values to a
common scale. This lets the agent quantify whether "lower price but slower delivery" is a better
or worse deal overall.
You might also consider techniques like MESO (Multiple Equivalent Simultaneous Offers) —
where the agent presents several offers at once that are equally valuable to itself but differ in
how terms are distributed (e.g., "Option A: lower price but longer payment terms" vs "Option B:
higher price but pay in 30 days"). This can help reveal the other party's preferences and move
the negotiation forward