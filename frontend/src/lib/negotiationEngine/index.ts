import { deriveDecision } from "../strategy";
import { computeUtility, formatTerms } from "../utilityFunction";
import {
  BuyerDecision,
  BuyerPreferences,
  NegotiationEvent,
  NegotiationState,
  OfferTerms,
  SupplierOfferInput,
} from "../types";

const nowIso = () => new Date().toISOString();

const makeId = () => crypto.randomUUID?.() ?? `evt-${Math.random().toString(36).slice(2, 9)}`;

function buildBuyerMessage(
  decision: BuyerDecision,
  counterOffers: OfferTerms[],
  rationale: string,
) {
  if (decision === "ACCEPT") {
    return ["Buyer accepts the proposal.", rationale].join("\n");
  }

  if (decision === "REJECT") {
    return [
      "Buyer rejects this round.",
      rationale,
      "Please adjust price, payment terms, delivery, or contract length within the buyer limits.",
    ].join("\n");
  }

  const offerLines = counterOffers.map((offer, index) =>
    `Option ${index + 1}: ${formatTerms(offer)}`,
  );

  return [
    "Buyer proposes MESO counteroffers.",
    rationale,
    offerLines.length > 0 ? offerLines.join("\n") : "",
    "Pick an option or reply with a revised offer.",
  ]
    .filter(Boolean)
    .join("\n");
}

export function startNegotiation(profile: BuyerPreferences): NegotiationState {
  const opening: NegotiationEvent = {
    id: makeId(),
    actor: "buyer",
    title: "Buyer ready",
    at: nowIso(),
    message:
      "I am the buyer agent. Please send your opening offer with price, payment days, delivery days, and contract length.",
    status: "pending",
  };

  return {
    round: 1,
    status: "pending",
    history: [opening],
    buyerProfile: profile,
  };
}

export function processSupplierOffer(
  state: NegotiationState,
  profile: BuyerPreferences,
  supplierOffer: SupplierOfferInput,
): NegotiationState {
  const supplierEvent: NegotiationEvent = {
    id: makeId(),
    actor: "supplier",
    title: `Supplier offer (round ${state.round})`,
    at: nowIso(),
    message:
      supplierOffer.note?.trim() ||
      `Supplier proposed ${formatTerms(supplierOffer.terms)}.`,
    terms: supplierOffer.terms,
  };

  const decision = deriveDecision(state, profile, supplierOffer.terms);
  const buyerMessage = buildBuyerMessage(
    decision.decision,
    decision.counterOffers,
    decision.rationale,
  );

  const buyerEvent: NegotiationEvent = {
    id: makeId(),
    actor: "buyer",
    title: decision.decision === "COUNTER" ? "Buyer counter" : "Buyer decision",
    at: nowIso(),
    message: buyerMessage,
    decision: decision.decision,
    status: decision.status,
    terms: decision.counterOffers[0] ?? supplierOffer.terms,
    counterOffers: decision.counterOffers,
    utility: decision.utility,
    breakdown: decision.breakdown,
    rationale: decision.rationale,
  };

  const history = [...state.history, supplierEvent, buyerEvent];
  const isTerminal = decision.status === "accepted" || decision.status === "rejected";

  return {
    round: isTerminal ? state.round : state.round + 1,
    status: decision.status,
    history,
    lastUtility: decision.utility,
    buyerProfile: profile,
    lastDecision: decision.decision,
  };
}

export function evaluateStandalone(
  terms: OfferTerms,
  profile: BuyerPreferences,
) {
  return computeUtility(terms, profile);
}
