import { describe, expect, it } from "vitest";

import {
  buildStartSessionPayload,
  createSessionConfig,
  validateSessionConfig,
} from "@/lib/session-config";

const defaults = {
  defaultStrategy: "BASELINE",
  maxRounds: 8,
  riskOfWalkaway: 0.15,
  buyerProfile: {
    idealOffer: {
      price: 90,
      paymentDays: 60,
      deliveryDays: 7,
      contractMonths: 6,
    },
    reservationOffer: {
      price: 120,
      paymentDays: 30,
      deliveryDays: 30,
      contractMonths: 24,
    },
    weights: {
      price: 0.4,
      paymentDays: 0.2,
      deliveryDays: 0.25,
      contractMonths: 0.15,
    },
    reservationUtility: 0,
  },
  bounds: {
    minPrice: 80,
    maxPrice: 120,
    minPaymentDays: 30,
    maxPaymentDays: 90,
    minDeliveryDays: 7,
    maxDeliveryDays: 30,
    minContractMonths: 3,
    maxContractMonths: 24,
  },
};

describe("session-config", () => {
  it("creates editable session configuration from backend defaults", () => {
    expect(createSessionConfig(defaults)).toEqual({
      maxRounds: "8",
      idealOffer: {
        price: "90",
        paymentDays: "60",
        deliveryDays: "7",
        contractMonths: "6",
      },
      reservationOffer: {
        price: "120",
        paymentDays: "30",
        deliveryDays: "30",
        contractMonths: "24",
      },
    });
  });

  it("rejects inconsistent goal and limit pairs", () => {
    const config = {
      ...createSessionConfig(defaults),
      idealOffer: {
        price: "121",
        paymentDays: "20",
        deliveryDays: "15",
        contractMonths: "25",
      },
    };

    expect(validateSessionConfig(config, defaults.bounds)).toContain(
      "Price goal must be less than or equal to the price limit.",
    );
  });

  it("builds the start-session payload with numeric values", () => {
    const payload = buildStartSessionPayload(createSessionConfig(defaults), defaults);

    expect(payload).toEqual({
      maxRounds: 8,
      buyerProfile: {
        idealOffer: {
          price: 90,
          paymentDays: 60,
          deliveryDays: 7,
          contractMonths: 6,
        },
        reservationOffer: {
          price: 120,
          paymentDays: 30,
          deliveryDays: 30,
          contractMonths: 24,
        },
        weights: {
          price: 0.4,
          paymentDays: 0.2,
          deliveryDays: 0.25,
          contractMonths: 0.15,
        },
        reservationUtility: 0,
      },
    });
  });
});
