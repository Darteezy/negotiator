const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "/api/negotiations";
const AI_BASE = "/api/ai";

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
    ...options,
  });

  const hasJsonBody = response.headers
    .get("content-type")
    ?.includes("application/json");
  const payload = hasJsonBody ? await response.json() : null;

  if (!response.ok) {
    throw new Error(
      payload?.message ?? `Request failed with status ${response.status}`,
    );
  }

  return payload;
}

export function fetchSessionDefaults() {
  return request("/config/defaults");
}

export function startSession(payload) {
  return request("/sessions", {
    method: "POST",
    body: payload ? JSON.stringify(payload) : null,
  });
}

export function fetchSession(sessionId) {
  return request(`/sessions/${sessionId}`);
}

export function submitSupplierOffer(sessionId, offer) {
  return request(`/sessions/${sessionId}/offers`, {
    method: "POST",
    body: JSON.stringify(offer),
  });
}

export function parseSupplierOfferWithAi(payload) {
  return requestAbsolute(`${AI_BASE}/parse-offer`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

async function requestAbsolute(path, options = {}) {
  const response = await fetch(path, {
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
    ...options,
  });

  const hasJsonBody = response.headers
    .get("content-type")
    ?.includes("application/json");
  const payload = hasJsonBody ? await response.json() : null;

  if (!response.ok) {
    throw new Error(
      payload?.message ?? `Request failed with status ${response.status}`,
    );
  }

  return payload;
}
