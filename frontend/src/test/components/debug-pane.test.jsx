import { render, screen } from "@testing-library/react";

import { DebugPane } from "@/components/debug-pane";

describe("DebugPane", () => {
  it("shows hidden buyer reasoning and metrics in the debug screen", () => {
    render(
      <DebugPane
        session={{
          conversation: [
            {
              eventType: "SUPPLIER_OFFER",
              actor: "supplier",
              title: "Supplier proposal",
              message: "Supplier fallback",
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
              message: "Counter explanation hidden from supplier.",
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

    expect(screen.getAllByRole("link", { name: "M2" }).length).toBeGreaterThan(
      0,
    );
    expect(screen.getAllByRole("link", { name: "M3" }).length).toBeGreaterThan(
      0,
    );
    expect(screen.getByText("Decision computed")).toBeInTheDocument();
    expect(screen.getByText("Buyer utility")).toBeInTheDocument();
    expect(screen.getAllByText("Counter to close gap").length).toBeGreaterThan(
      0,
    );
    expect(
      screen.getByText("Option 1 trades price for payment flexibility."),
    ).toBeInTheDocument();
  });
});
