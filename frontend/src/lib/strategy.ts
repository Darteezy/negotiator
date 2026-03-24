import { computeUtility, describeUtilityLevel } from "./utilityFunction";
import {
  BuyerPreferences,
  DecisionOutcome,
  NegotiationState,
  OfferTerms,
} from "./types";

const clamp = (value: number, minimum: number, maximum: number) =>
  Math.min(Math.max(value, minimum), maximum);

function stepLowerIsBetter(
  ideal: number,
  supplierValue: number,
  limit: number,
  concession: number,
) {
  if (supplierValue <= ideal) {
    return supplierValue;
  }

  const move = (supplierValue - ideal) * concession;
  return clamp(ideal + move, 0, limit);
}

function stepRange(
  ideal: number,
  supplierValue: number,
  min: number,
  max: number,
  concession: number,
) {
  const move = (supplierValue - ideal) * concession;
  return clamp(ideal + move, min, max);
}

function dynamicThreshold(
  start: number,
  floor: number,
  progress: number,
  decay: number,
) {
  return Math.max(floor, start - decay * progress);
}

function createBaseCounter(
  supplier: OfferTerms,
  profile: BuyerPreferences,
  concession: number,
): OfferTerms {
  return {
    price: stepLowerIsBetter(
      profile.price.preferred,
      supplier.price,
      profile.price.max,
      concession,
    ),
    paymentDays: stepLowerIsBetter(
      profile.paymentDays.preferred,
      supplier.paymentDays,
      profile.paymentDays.max,
      concession * 0.92,
    ),
    deliveryDays: stepLowerIsBetter(
      profile.deliveryDays.preferred,
      supplier.deliveryDays,
      profile.deliveryDays.max,
      concession * 0.92,
    ),
    contractMonths: stepRange(
      profile.contractMonths.preferred,
      supplier.contractMonths,
      profile.contractMonths.min,
      profile.contractMonths.max,
      concession * 0.9,
    ),
  };
}

function buildMesoOffers(
  base: OfferTerms,
  supplier: OfferTerms,
  profile: BuyerPreferences,
) {
  const priceDelta = Math.max(1, Math.round(Math.abs(supplier.price - base.price) * 0.35));
  const deliveryDelta = Math.max(
    1,
    Math.round(Math.abs(supplier.deliveryDays - base.deliveryDays) * 0.45),
  );
  const paymentDelta = Math.max(
    1,
    Math.round(Math.abs(supplier.paymentDays - base.paymentDays) * 0.4),
  );

  const options: OfferTerms[] = [
    {
      // Margin-focused: better price, slower delivery
      price: clamp(base.price - priceDelta, 0, profile.price.max),
      paymentDays: clamp(
        base.paymentDays + paymentDelta,
        0,
        profile.paymentDays.max,
      ),
      deliveryDays: clamp(
        base.deliveryDays + deliveryDelta,
        0,
        profile.deliveryDays.max,
      ),
      contractMonths: base.contractMonths,
    },
    {
      // Speed-focused: faster delivery, slightly higher price
      price: clamp(base.price + priceDelta, 0, profile.price.max),
      paymentDays: clamp(
        base.paymentDays - Math.max(1, paymentDelta - 1),
        0,
        profile.paymentDays.max,
      ),
      deliveryDays: clamp(base.deliveryDays - deliveryDelta, 0, profile.deliveryDays.max),
      contractMonths: clamp(
        base.contractMonths + 1,
        profile.contractMonths.min,
        profile.contractMonths.max,
      ),
    },
    {
      // Cash-flow balance: improved payment terms, neutral delivery
      price: clamp(base.price, 0, profile.price.max),
      paymentDays: clamp(
        base.paymentDays + paymentDelta,
        0,
        profile.paymentDays.max,
      ),
      deliveryDays: clamp(base.deliveryDays, 0, profile.deliveryDays.max),
      contractMonths: clamp(
        base.contractMonths - 1,
        profile.contractMonths.min,
        profile.contractMonths.max,
      ),
    },
  ];

  return options;
}

export function deriveDecision(
  state: NegotiationState,
  profile: BuyerPreferences,
  supplier: OfferTerms,
): DecisionOutcome {
  const evaluation = computeUtility(supplier, profile);
  const progress = (state.round - 1) / Math.max(profile.maxRounds - 1, 1);
  const acceptThreshold = dynamicThreshold(
    profile.thresholds.acceptStart,
    profile.thresholds.acceptFloor,
    progress,
    0.25,
  );
  const counterThreshold = dynamicThreshold(
    profile.thresholds.counterStart,
    profile.thresholds.counterFloor,
    progress,
    0.2,
  );

  if (evaluation.violations.length > 0) {
    return {
      decision: "REJECT",
      status: "rejected",
      counterOffers: [],
      rationale: evaluation.violations.join(" "),
      utility: evaluation.utility,
      breakdown: evaluation.breakdown,
    };
  }

  if (evaluation.utility >= acceptThreshold) {
    return {
      decision: "ACCEPT",
      status: "accepted",
      counterOffers: [],
      rationale: `Offer utility is ${describeUtilityLevel(evaluation.utility)} (>= ${acceptThreshold.toFixed(2)} threshold).`,
      utility: evaluation.utility,
      breakdown: evaluation.breakdown,
    };
  }

  if (evaluation.utility >= counterThreshold) {
    const concession = 0.25 + progress * 0.55;
    const baseCounter = createBaseCounter(supplier, profile, concession);
    const mesoOptions = buildMesoOffers(baseCounter, supplier, profile)
      .map((terms) => ({ terms, utility: computeUtility(terms, profile) }))
      .filter((option) => option.utility.violations.length === 0);

    const baseUtility = computeUtility(baseCounter, profile).utility;
    const similar = mesoOptions
      .filter((option) => Math.abs(option.utility.utility - baseUtility) <= 0.08)
      .slice(0, 2)
      .map((option) => option.terms);

    const counterOffers = [baseCounter, ...similar];

    return {
      decision: "COUNTER",
      status: "countered",
      counterOffers,
      rationale: `Utility ${evaluation.utility.toFixed(2)} is within bargaining range (>= ${counterThreshold.toFixed(2)}). Offering MESO set with consistent utility.`,
      utility: evaluation.utility,
      breakdown: evaluation.breakdown,
    };
  }

  return {
    decision: "REJECT",
    status: "rejected",
    counterOffers: [],
    rationale: `Utility ${evaluation.utility.toFixed(2)} is below the negotiation floor (${counterThreshold.toFixed(2)}).`,
    utility: evaluation.utility,
    breakdown: evaluation.breakdown,
  };
}
