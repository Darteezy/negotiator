export type Actor = "buyer" | "supplier" | "system";

export interface OfferTerms {
  price: number;
  paymentDays: number;
  deliveryDays: number;
  contractMonths: number;
}

export interface SupplierConstraints {
  priceFloor?: number | null;
  paymentDaysCeiling?: number | null;
  deliveryDaysFloor?: number | null;
  contractMonthsFloor?: number | null;
}

export interface IssueWeights {
  price: number;
  paymentDays: number;
  deliveryDays: number;
  contractMonths: number;
}

export interface ApiBuyerProfile {
  idealOffer: OfferTerms;
  reservationOffer: OfferTerms;
  weights: IssueWeights;
  reservationUtility: number;
}

export interface ApiBounds {
  minPrice: number;
  maxPrice: number;
  minPaymentDays: number;
  maxPaymentDays: number;
  minDeliveryDays: number;
  maxDeliveryDays: number;
  minContractMonths: number;
  maxContractMonths: number;
}

export interface ApiEvaluation {
  buyerUtility: number;
  estimatedSupplierUtility: number;
  targetUtility: number;
  continuationValue: number;
  nashProduct: number;
}

export interface ApiConversationDebug {
  strategy?: string | null;
  strategyRationale?: string | null;
  switchTrigger?: string | null;
  reasonCode?: string | null;
  focusIssue?: string | null;
  evaluation?: ApiEvaluation | null;
  counterOfferSummary: string[];
}

export interface ApiConversationEvent {
  eventType: string;
  actor: Actor;
  title: string;
  message: string;
  at: string;
  terms?: OfferTerms | null;
  counterOffers: OfferTerms[];
  debug?: ApiConversationDebug | null;
}

export interface ApiStrategyHistory {
  roundNumber: number;
  previousStrategy?: string | null;
  nextStrategy: string;
  trigger: string;
  rationale: string;
  at: string;
}

export interface ApiStrategyDetails {
  name: string;
  label: string;
  summary: string;
  concessionStyle: string;
  boundaryStyle: string;
}

export interface ApiSessionDefaults {
  defaultStrategy: string;
  availableStrategies: string[];
  strategyDetails: ApiStrategyDetails[];
  maxRounds: number;
  riskOfWalkaway: number;
  buyerProfile: ApiBuyerProfile;
  bounds: ApiBounds;
}

export interface ApiNegotiationSession {
  id: string;
  sessionToken: string;
  strategy: string;
  currentRound: number;
  maxRounds: number;
  riskOfWalkaway: number;
  status: string;
  closed: boolean;
  buyerProfile: ApiBuyerProfile;
  bounds: ApiBounds;
  strategyHistory: ApiStrategyHistory[];
  strategyDetails: ApiStrategyDetails[];
  conversation: ApiConversationEvent[];
}
