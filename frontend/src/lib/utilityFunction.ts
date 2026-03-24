import {
  BuyerPreferences,
  OfferTerms,
  UtilityBreakdown,
  UtilityResult,
} from "./types";

const clamp = (value: number, minimum: number, maximum: number) =>
  Math.min(Math.max(value, minimum), maximum);

const round2 = (value: number) => Math.round(value * 100) / 100;

function scoreLowerIsBetter(value: number, ideal: number, limit: number) {
  if (value <= ideal) {
    return 1;
  }

  if (value >= limit) {
    return 0;
  }

  const span = limit - ideal || 1;
  return clamp(1 - (value - ideal) / span, 0, 1);
}

function scoreRangeCentered(
  value: number,
  ideal: number,
  minValue: number,
  maxValue: number,
) {
  if (value < minValue || value > maxValue) {
    return 0;
  }

  const span = Math.max(ideal - minValue, maxValue - ideal) || 1;
  return clamp(1 - Math.abs(value - ideal) / span, 0, 1);
}

export function computeUtility(
  terms: OfferTerms,
  profile: BuyerPreferences,
): UtilityResult {
  const weightSum =
    profile.weights.price +
    profile.weights.paymentDays +
    profile.weights.deliveryDays +
    profile.weights.contractMonths || 1;

  const violations: string[] = [];

  if (terms.price > profile.price.max) {
    violations.push("Price exceeds buyer maximum.");
  }

  if (terms.paymentDays > profile.paymentDays.max) {
    violations.push("Payment terms exceed buyer maximum.");
  }

  if (terms.deliveryDays > profile.deliveryDays.max) {
    violations.push("Delivery time exceeds buyer maximum.");
  }

  if (
    terms.contractMonths < profile.contractMonths.min ||
    terms.contractMonths > profile.contractMonths.max
  ) {
    violations.push("Contract length outside acceptable range.");
  }

  const breakdown: UtilityBreakdown = {
    price: {
      score: scoreLowerIsBetter(
        terms.price,
        profile.price.preferred,
        profile.price.max,
      ),
      weighted: 0,
      weightShare: profile.weights.price / weightSum,
    },
    paymentDays: {
      score: scoreLowerIsBetter(
        terms.paymentDays,
        profile.paymentDays.preferred,
        profile.paymentDays.max,
      ),
      weighted: 0,
      weightShare: profile.weights.paymentDays / weightSum,
    },
    deliveryDays: {
      score: scoreLowerIsBetter(
        terms.deliveryDays,
        profile.deliveryDays.preferred,
        profile.deliveryDays.max,
      ),
      weighted: 0,
      weightShare: profile.weights.deliveryDays / weightSum,
    },
    contractMonths: {
      score: scoreRangeCentered(
        terms.contractMonths,
        profile.contractMonths.preferred,
        profile.contractMonths.min,
        profile.contractMonths.max,
      ),
      weighted: 0,
      weightShare: profile.weights.contractMonths / weightSum,
    },
  };

  const utility = round2(
    Object.values(breakdown).reduce((total, part) => {
      const contribution = part.score * part.weightShare;
      part.weighted = round2(contribution);
      return total + contribution;
    }, 0),
  );

  return {
    utility: round2(utility),
    breakdown,
    violations,
  };
}

export function describeUtilityLevel(utility: number) {
  if (utility >= 0.9) return "excellent";
  if (utility >= 0.75) return "strong";
  if (utility >= 0.6) return "workable";
  if (utility >= 0.45) return "weak";
  return "poor";
}

export function formatTerms(terms: OfferTerms) {
  return `${terms.price.toFixed(2)}€ | pay in ${terms.paymentDays}d | deliver in ${terms.deliveryDays}d | ${terms.contractMonths}m contract`;
}
