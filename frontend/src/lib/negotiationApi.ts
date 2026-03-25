import type {
  ApiNegotiationSession,
  ApiSessionDefaults,
  OfferTerms,
  SupplierConstraints,
} from "@/lib/types";

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? "").replace(
  /\/$/,
  "",
);

interface StartSessionPayload {
  strategy: string;
  maxRounds: number;
  riskOfWalkaway: number;
  buyerProfile: {
    idealOffer: OfferTerms;
    reservationOffer: OfferTerms;
    weights: {
      price: number;
      paymentDays: number;
      deliveryDays: number;
      contractMonths: number;
    };
    reservationUtility: number;
  };
}

interface UpdateSessionSettingsPayload extends StartSessionPayload {
  bounds: {
    minPrice: number;
    maxPrice: number;
    minPaymentDays: number;
    maxPaymentDays: number;
    minDeliveryDays: number;
    maxDeliveryDays: number;
    minContractMonths: number;
    maxContractMonths: number;
  };
}

interface SubmitSupplierOfferPayload extends OfferTerms {
  supplierMessage?: string;
  supplierConstraints?: SupplierConstraints;
}

interface ParseSupplierOfferPayload {
  supplierMessage: string;
  referenceTerms: OfferTerms;
  counterOffers: OfferTerms[];
}

interface ParseSupplierOfferResponse {
  price?: number | null;
  paymentDays?: number | null;
  deliveryDays?: number | null;
  contractMonths?: number | null;
  supplierConstraints?: SupplierConstraints | null;
}

async function requestJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
  });

  if (!response.ok) {
    const message = await readErrorMessage(response);
    throw new Error(message);
  }

  return response.json() as Promise<T>;
}

async function readErrorMessage(response: Response) {
  const fallback = `Request failed with status ${response.status}`;
  const bodyText = await response.text();

  if (!bodyText.trim()) {
    return fallback;
  }

  try {
    const payload = JSON.parse(bodyText);
    if (typeof payload === "string" && payload.trim()) {
      return payload;
    }
    if (payload?.message && typeof payload.message === "string") {
      return payload.message;
    }
    if (payload?.error && typeof payload.error === "string") {
      return payload.error;
    }
    return fallback;
  } catch {
    return bodyText.trim() || fallback;
  }
}

export function fetchNegotiationDefaults() {
  return requestJson<ApiSessionDefaults>("/api/negotiations/config/defaults", {
    method: "GET",
  });
}

export function startNegotiationSession(payload: StartSessionPayload) {
  return requestJson<ApiNegotiationSession>("/api/negotiations/sessions", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function submitSupplierOffer(
  sessionId: string,
  payload: SubmitSupplierOfferPayload,
) {
  return requestJson<ApiNegotiationSession>(
    `/api/negotiations/sessions/${sessionId}/offers`,
    {
      method: "POST",
      body: JSON.stringify(payload),
    },
  );
}

export function updateNegotiationSettings(
  sessionId: string,
  payload: UpdateSessionSettingsPayload,
) {
  return requestJson<ApiNegotiationSession>(
    `/api/negotiations/sessions/${sessionId}/settings`,
    {
      method: "PUT",
      body: JSON.stringify(payload),
    },
  );
}

export function parseSupplierOffer(payload: ParseSupplierOfferPayload) {
  return requestJson<ParseSupplierOfferResponse>("/api/ai/parse-offer", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}
