export type NegotiationStatus = "pending" | "countered" | "accepted" | "rejected";

export type BuyerDecision = "ACCEPT" | "COUNTER" | "REJECT";

export type Actor = "buyer" | "supplier" | "system";

export interface OfferTerms {
  price: number;
  paymentDays: number;
  deliveryDays: number;
  contractMonths: number;
}

export interface BuyerPreferences {
  price: {
    preferred: number;
    max: number;
  };
  paymentDays: {
    preferred: number;
    max: number;
  };
  deliveryDays: {
    preferred: number;
    max: number;
  };
  contractMonths: {
    preferred: number;
    min: number;
    max: number;
  };
  weights: {
    price: number;
    paymentDays: number;
    deliveryDays: number;
    contractMonths: number;
  };
  thresholds: {
    acceptStart: number;
    acceptFloor: number;
    counterStart: number;
    counterFloor: number;
  };
  maxRounds: number;
}

export interface DimensionScore {
  score: number;
  weighted: number;
  weightShare: number;
}

export type UtilityBreakdown = Record<keyof OfferTerms, DimensionScore>;

export interface UtilityResult {
  utility: number;
  breakdown: UtilityBreakdown;
  violations: string[];
}

export interface NegotiationEvent {
  id: string;
  actor: Actor;
  title: string;
  at: string;
  message: string;
  terms?: OfferTerms;
  utility?: number;
  breakdown?: UtilityBreakdown;
  decision?: BuyerDecision;
  status?: NegotiationStatus;
  counterOffers?: OfferTerms[];
  mesoGroup?: string;
  rationale?: string;
}

export interface NegotiationState {
  round: number;
  status: NegotiationStatus;
  history: NegotiationEvent[];
  lastUtility?: number;
  buyerProfile: BuyerPreferences;
  lastDecision?: BuyerDecision;
}

export interface SupplierOfferInput {
  terms: OfferTerms;
  note?: string;
}

export interface DecisionOutcome {
  decision: BuyerDecision;
  status: NegotiationStatus;
  counterOffers: OfferTerms[];
  rationale: string;
  utility: number;
  breakdown: UtilityBreakdown;
}
