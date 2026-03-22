import { render, screen } from "@testing-library/react";

import { ConversationPane } from "@/components/conversation-pane";

describe("ConversationPane", () => {
  it("renders an official buyer-first chat transcript without exposing debug metrics", async () => {
    render(
      <ConversationPane
        loading={false}
        session={{
          currentRound: 2,
          maxRounds: 8,
          strategy: "MESO",
          status: "COUNTERED",
          conversation: [
            {
              eventType: "SUPPLIER_OFFER",
              actor: "supplier",
              title: "Supplier proposal",
              message: "Supplier fallback message",
              at: "2026-03-22T12:00:00Z",
              terms: {
                price: 104,
                paymentDays: 45,
                deliveryDays: 10,
                contractMonths: 12,
              },
              counterOffers: [],
              debug: null,
            },
            {
              eventType: "BUYER_REPLY",
              actor: "buyer",
              title: "Buyer reply",
              message:
                "Buyer counters to protect utility while keeping the deal alive.",
              at: "2026-03-22T12:01:00Z",
              terms: {
                price: 101,
                paymentDays: 40,
                deliveryDays: 9,
                contractMonths: 12,
              },
              counterOffers: [
                {
                  price: 101,
                  paymentDays: 40,
                  deliveryDays: 9,
                  contractMonths: 12,
                },
              ],
              debug: {
                strategy: "MESO",
                strategyRationale:
                  "MESO keeps multiple efficient options open early.",
                switchTrigger: null,
                reasonCode: "COUNTER_TO_CLOSE_GAP",
                focusIssue: "PRICE",
                evaluation: {
                  buyerUtility: 0.78,
                  targetUtility: 0.8,
                  estimatedSupplierUtility: 0.55,
                  nashProduct: 0.43,
                },
                counterOfferSummary: [
                  "Option 1 trades price for payment flexibility.",
                ],
              },
            },
          ],
        }}
        supplierMessages={[
          "Price 104, payment 45 days, delivery 10 days, contract 12 months.",
        ]}
      />,
    );

    expect(screen.getByText("Round 2 / 8")).toBeInTheDocument();
    expect(screen.getByText("Countered")).toBeInTheDocument();
    expect(screen.getAllByText("Meso").length).toBeGreaterThan(0);
    expect(screen.getByRole("link", { name: "M1" })).toBeInTheDocument();
    expect(
      screen.getByText(/thank you for engaging with our procurement team/i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "Price 104, payment 45 days, delivery 10 days, contract 12 months.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /after review, the buyer can continue under the following terms/i,
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText("Buyer utility")).not.toBeInTheDocument();
    expect(
      screen.queryByText(/MESO keeps multiple efficient options open early/i),
    ).not.toBeInTheDocument();
  });

  it("renders an acceptance message and accepted status when the negotiation closes", () => {
    render(
      <ConversationPane
        loading={false}
        session={{
          currentRound: 2,
          maxRounds: 8,
          strategy: "MESO",
          status: "ACCEPTED",
          closed: true,
          rounds: [
            {
              roundNumber: 1,
              supplierOffer: {
                at: "2026-03-22T12:00:00Z",
                terms: {
                  price: 104,
                  paymentDays: 45,
                  deliveryDays: 10,
                  contractMonths: 12,
                },
              },
              buyerReply: {
                decision: "COUNTER",
                resultingStatus: "COUNTERED",
                decidedAt: "2026-03-22T12:01:00Z",
                counterOffer: {
                  price: 101,
                  paymentDays: 40,
                  deliveryDays: 9,
                  contractMonths: 12,
                },
                counterOffers: [
                  {
                    price: 101,
                    paymentDays: 40,
                    deliveryDays: 9,
                    contractMonths: 12,
                  },
                ],
              },
            },
            {
              roundNumber: 2,
              supplierOffer: {
                at: "2026-03-22T12:02:00Z",
                terms: {
                  price: 101,
                  paymentDays: 40,
                  deliveryDays: 9,
                  contractMonths: 12,
                },
              },
              buyerReply: {
                decision: "ACCEPT",
                resultingStatus: "ACCEPTED",
                explanation:
                  "Accepted because the supplier agreed to the buyer's active offer from the previous round.",
                decidedAt: "2026-03-22T12:03:00Z",
                counterOffer: null,
                counterOffers: [],
              },
            },
          ],
          conversation: [],
          strategyHistory: [],
        }}
        supplierMessages={[
          "Price 104, payment 45 days, delivery 10 days, contract 12 months.",
          "Okay we agree with option 1.",
        ]}
      />,
    );

    expect(screen.getByText("Accepted")).toBeInTheDocument();
    expect(
      screen.getByText(
        /the buyer accepts the agreed terms and closes the negotiation/i,
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /accepted because the supplier agreed to the buyer's active offer/i,
      ),
    ).toBeInTheDocument();
  });
});
