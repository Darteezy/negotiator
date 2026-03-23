import { describe, expect, it } from "vitest";

import { buildChatTranscript } from "@/lib/conversation-view";

describe("conversation-view", () => {
  it("shows a close-ready buyer reply instead of closing immediately", () => {
    const transcript = buildChatTranscript({
      conversation: [],
      rounds: [
        {
          roundNumber: 5,
          supplierOffer: {
            at: "2026-03-23T13:05:00Z",
            message: "Increase price to 120 and increase payment days to 60 days",
            terms: {
              price: 120,
              paymentDays: 60,
              deliveryDays: 7,
              contractMonths: 12,
            },
          },
          buyerReply: {
            decision: "COUNTER",
            resultingStatus: "COUNTERED",
            decidedAt: "2026-03-23T13:05:05Z",
            explanation:
              "Buyer is ready to close on these terms. Reply with accept to finalize the deal.",
            counterOffer: {
              price: 120,
              paymentDays: 60,
              deliveryDays: 7,
              contractMonths: 12,
            },
            counterOffers: [
              {
                price: 120,
                paymentDays: 60,
                deliveryDays: 7,
                contractMonths: 12,
              },
            ],
          },
        },
      ],
      strategyHistory: [],
    });

    expect(transcript.at(-1)?.message).toContain(
      "Buyer is ready to close on these terms:",
    );
    expect(transcript.at(-1)?.message).toContain(
      "Reply with accept to finalize the deal.",
    );
  });
});
