import { formatMoney } from "@/lib/format";

export const SESSION_TERM_FIELDS = [
  {
    key: "price",
    label: "Price",
    validationLabel: "price",
    unit: "EUR",
    step: "0.01",
    goalDescription: "Buyer goal: pay less.",
    limitDescription: "Buyer limit: highest acceptable price.",
    minKey: "minPrice",
    maxKey: "maxPrice",
  },
  {
    key: "paymentDays",
    label: "Payment days",
    validationLabel: "payment days",
    unit: "days",
    step: "1",
    goalDescription: "Buyer goal: pay later.",
    limitDescription: "Buyer limit: shortest acceptable payment term.",
    minKey: "minPaymentDays",
    maxKey: "maxPaymentDays",
  },
  {
    key: "deliveryDays",
    label: "Delivery time",
    validationLabel: "delivery time",
    unit: "days",
    step: "1",
    goalDescription: "Buyer goal: receive goods faster.",
    limitDescription: "Buyer limit: slowest acceptable delivery.",
    minKey: "minDeliveryDays",
    maxKey: "maxDeliveryDays",
  },
  {
    key: "contractMonths",
    label: "Contract length",
    validationLabel: "contract length",
    unit: "months",
    step: "1",
    goalDescription: "Buyer goal: shorter commitment.",
    limitDescription: "Buyer limit: longest acceptable contract.",
    minKey: "minContractMonths",
    maxKey: "maxContractMonths",
  },
];

export function createSessionConfig(defaults) {
  return {
    strategy: defaults?.defaultStrategy ?? "MESO",
    maxRounds: String(defaults?.maxRounds ?? 6),
    idealOffer: stringifyOffer(defaults?.buyerProfile?.idealOffer),
    reservationOffer: stringifyOffer(defaults?.buyerProfile?.reservationOffer),
  };
}

export function getSessionTermField(key) {
  return SESSION_TERM_FIELDS.find((field) => field.key === key) ?? null;
}

export function formatTermRange(fieldKey, bounds) {
  const field = getSessionTermField(fieldKey);

  if (!field || !bounds) {
    return null;
  }

  const minimum = bounds[field.minKey];
  const maximum = bounds[field.maxKey];
  const renderedRange =
    field.key === "price"
      ? `${formatMoney(minimum)} and ${formatMoney(maximum)}`
      : `${minimum} and ${maximum} ${field.unit}`;

  return `${field.label} must stay between ${renderedRange}.`;
}

export function validateSessionConfig(config, bounds) {
  const errors = [];
  const maxRounds = Number.parseInt(config?.maxRounds ?? "", 10);

  if (!Number.isInteger(maxRounds) || maxRounds <= 0) {
    errors.push("Max rounds must be a positive whole number.");
  }

  const idealOffer = parseOffer(config?.idealOffer);
  const reservationOffer = parseOffer(config?.reservationOffer);

  for (const field of SESSION_TERM_FIELDS) {
    if (!Number.isFinite(idealOffer[field.key])) {
      errors.push(`${field.label} goal is required.`);
    }

    if (!Number.isFinite(reservationOffer[field.key])) {
      errors.push(`${field.label} limit is required.`);
    }
  }

  if (errors.length > 0) {
    return errors;
  }

  if (idealOffer.price > reservationOffer.price) {
    errors.push("Price goal must be less than or equal to the price limit.");
  }

  if (idealOffer.paymentDays < reservationOffer.paymentDays) {
    errors.push(
      "Payment days goal must be greater than or equal to the payment days limit.",
    );
  }

  if (idealOffer.deliveryDays > reservationOffer.deliveryDays) {
    errors.push(
      "Delivery goal must be less than or equal to the delivery limit.",
    );
  }

  if (idealOffer.contractMonths > reservationOffer.contractMonths) {
    errors.push(
      "Contract length goal must be less than or equal to the contract length limit.",
    );
  }

  if (bounds) {
    for (const field of SESSION_TERM_FIELDS) {
      const minimum = Number(bounds[field.minKey]);
      const maximum = Number(bounds[field.maxKey]);

      if (
        idealOffer[field.key] < minimum ||
        idealOffer[field.key] > maximum ||
        reservationOffer[field.key] < minimum ||
        reservationOffer[field.key] > maximum
      ) {
        errors.push(
          `${field.label} must stay between ${minimum} and ${maximum} ${field.unit}.`,
        );
      }
    }
  }

  return errors;
}

export function buildStartSessionPayload(config, defaults) {
  const idealOffer = parseOffer(config.idealOffer);
  const reservationOffer = parseOffer(config.reservationOffer);

  return {
    strategy: config.strategy,
    maxRounds: Number.parseInt(config.maxRounds, 10),
    riskOfWalkaway: Number(defaults.riskOfWalkaway),
    buyerProfile: {
      idealOffer,
      reservationOffer,
      weights: {
        price: Number(defaults.buyerProfile.weights.price),
        paymentDays: Number(defaults.buyerProfile.weights.paymentDays),
        deliveryDays: Number(defaults.buyerProfile.weights.deliveryDays),
        contractMonths: Number(defaults.buyerProfile.weights.contractMonths),
      },
      reservationUtility: Number(defaults.buyerProfile.reservationUtility),
    },
    bounds: {
      minPrice: Number(defaults.bounds.minPrice),
      maxPrice: Number(defaults.bounds.maxPrice),
      minPaymentDays: Number(defaults.bounds.minPaymentDays),
      maxPaymentDays: Number(defaults.bounds.maxPaymentDays),
      minDeliveryDays: Number(defaults.bounds.minDeliveryDays),
      maxDeliveryDays: Number(defaults.bounds.maxDeliveryDays),
      minContractMonths: Number(defaults.bounds.minContractMonths),
      maxContractMonths: Number(defaults.bounds.maxContractMonths),
    },
  };
}

function stringifyOffer(offer = {}) {
  return {
    price: stringifyValue(offer.price),
    paymentDays: stringifyValue(offer.paymentDays),
    deliveryDays: stringifyValue(offer.deliveryDays),
    contractMonths: stringifyValue(offer.contractMonths),
  };
}

function stringifyValue(value) {
  return value === null || value === undefined ? "" : String(value);
}

function parseOffer(offer = {}) {
  return {
    price: Number(offer.price),
    paymentDays: Math.round(Number(offer.paymentDays)),
    deliveryDays: Math.round(Number(offer.deliveryDays)),
    contractMonths: Math.round(Number(offer.contractMonths)),
  };
}
