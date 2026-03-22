import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import App from "@/App";
import * as negotiationApi from "@/api/negotiation";

vi.mock("@/api/negotiation", async () => {
  const actual = await vi.importActual("@/api/negotiation");

  return {
    ...actual,
    fetchSessionDefaults: vi.fn(),
    parseSupplierOfferWithAi: vi.fn(),
    startSession: vi.fn(),
    submitSupplierOffer: vi.fn(),
  };
});

describe("App", () => {
  it("reuses the latest terms for partial supplier updates and keeps reasoning in the debug screen", async () => {
    negotiationApi.fetchSessionDefaults.mockResolvedValue({
      defaultStrategy: "MESO",
      availableStrategies: ["MESO", "TIT_FOR_TAT"],
      maxRounds: 10,
      bounds: {
        minPrice: 80,
        maxPrice: 120,
        minPaymentDays: 30,
        maxPaymentDays: 90,
        minDeliveryDays: 3,
        maxDeliveryDays: 14,
        minContractMonths: 3,
        maxContractMonths: 24,
      },
    });

    negotiationApi.startSession.mockResolvedValue({
      id: "session-1",
      strategy: "MESO",
      currentRound: 1,
      maxRounds: 10,
      status: "PENDING",
      closed: false,
      rounds: [
        {
          roundNumber: 1,
          supplierOffer: {
            at: "2026-03-22T12:01:00Z",
            terms: {
              price: 104,
              paymentDays: 45,
              deliveryDays: 10,
              contractMonths: 12,
            },
          },
          buyerReply: {
            explanation: "Buyer counters while preserving margin.",
            decidedAt: "2026-03-22T12:02:00Z",
            reasonCode: "COUNTER_TO_CLOSE_GAP",
            strategyUsed: "MESO",
            strategyRationale: "MESO explores efficient bundles first.",
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
            evaluation: {
              buyerUtility: 0.76,
              targetUtility: 0.8,
              estimatedSupplierUtility: 0.5,
              nashProduct: 0.38,
            },
          },
        },
      ],
      strategyHistory: [
        {
          trigger: "INITIAL_SELECTION",
          nextStrategy: "MESO",
          rationale: "Open with multiple efficient packages.",
          at: "2026-03-22T12:00:00Z",
        },
      ],
      conversation: [
        {
          eventType: "STRATEGY_CHANGE",
          actor: "system",
          title: "Opening strategy selected",
          message: "Open with multiple efficient packages.",
          at: "2026-03-22T12:00:00Z",
          terms: null,
          counterOffers: [],
          debug: {
            strategy: "MESO",
            strategyRationale: "Open with multiple efficient packages.",
            switchTrigger: "INITIAL_SELECTION",
            reasonCode: null,
            focusIssue: null,
            evaluation: null,
            counterOfferSummary: [],
          },
        },
      ],
      bounds: {
        minPrice: 80,
        maxPrice: 120,
        minPaymentDays: 30,
        maxPaymentDays: 90,
        minDeliveryDays: 3,
        maxDeliveryDays: 14,
        minContractMonths: 3,
        maxContractMonths: 24,
      },
    });

    negotiationApi.submitSupplierOffer.mockResolvedValue({
      id: "session-1",
      strategy: "MESO",
      currentRound: 2,
      maxRounds: 10,
      status: "COUNTERED",
      closed: false,
      bounds: {
        minPrice: 80,
        maxPrice: 120,
        minPaymentDays: 30,
        maxPaymentDays: 90,
        minDeliveryDays: 3,
        maxDeliveryDays: 14,
        minContractMonths: 3,
        maxContractMonths: 24,
      },
      conversation: [
        {
          eventType: "STRATEGY_CHANGE",
          actor: "system",
          title: "Opening strategy selected",
          message: "Open with multiple efficient packages.",
          at: "2026-03-22T12:00:00Z",
          terms: null,
          counterOffers: [],
          debug: {
            strategy: "MESO",
            strategyRationale: "Open with multiple efficient packages.",
            switchTrigger: "INITIAL_SELECTION",
            reasonCode: null,
            focusIssue: null,
            evaluation: null,
            counterOfferSummary: [],
          },
        },
        {
          eventType: "SUPPLIER_OFFER",
          actor: "supplier",
          title: "Supplier proposal",
          message:
            "Supplier proposes price 104.00, payment in 45 days, delivery in 10 days, contract term 12 months.",
          at: "2026-03-22T12:01:00Z",
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
          message: "Buyer counters while preserving margin.",
          at: "2026-03-22T12:02:00Z",
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
            strategyRationale: "MESO explores efficient bundles first.",
            switchTrigger: null,
            reasonCode: "COUNTER_TO_CLOSE_GAP",
            focusIssue: "PRICE",
            evaluation: {
              buyerUtility: 0.76,
              targetUtility: 0.8,
              estimatedSupplierUtility: 0.5,
              nashProduct: 0.38,
            },
            counterOfferSummary: [
              "Option 1 improves buyer price while keeping contract length stable.",
            ],
          },
        },
      ],
    });

    const user = userEvent.setup();
    render(<App />);

    await user.click(screen.getByRole("button", { name: /connect to buyer/i }));

    await waitFor(() => {
      expect(negotiationApi.startSession).toHaveBeenCalledWith({
        strategy: "MESO",
        maxRounds: 10,
      });
    });

    await user.type(
      screen.getByRole("textbox", { name: /message to buyer/i }),
      "Please increase price to 104.",
    );

    await user.click(screen.getByRole("button", { name: /send offer/i }));

    await waitFor(() => {
      expect(negotiationApi.submitSupplierOffer).toHaveBeenCalledWith(
        "session-1",
        {
          price: 104,
          paymentDays: 40,
          deliveryDays: 9,
          contractMonths: 12,
          supplierConstraints: null,
        },
      );
    });

    expect(
      screen.getByText(/thank you for engaging with our procurement team/i),
    ).toBeInTheDocument();
    expect(screen.getByText("Round 2 / 10")).toBeInTheDocument();
    expect(screen.getByText("Countered")).toBeInTheDocument();
    expect(screen.getAllByText("Meso").length).toBeGreaterThan(0);
    expect(screen.getAllByRole("link", { name: "M2" }).length).toBeGreaterThan(
      0,
    );
    expect(
      screen.getAllByText("Please increase price to 104.").length,
    ).toBeGreaterThan(0);
    expect(
      (
        await screen.findAllByText(
          /after review, the buyer can continue under the following terms/i,
        )
      ).length,
    ).toBeGreaterThan(0);
    expect(screen.getByText("Decision computed")).toBeInTheDocument();
    expect(screen.getByText("Buyer utility")).toBeInTheDocument();
    expect(screen.getAllByText("Counter to close gap").length).toBeGreaterThan(
      0,
    );
  });
});
