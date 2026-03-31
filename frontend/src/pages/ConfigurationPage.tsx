import { useEffect, useState } from "react";
import { FormField } from "@/components/FormField";
import {
  fetchNegotiationDefaults,
  startNegotiationSession,
} from "@/lib/negotiationApi";
import {
  normalizeSessionBounds,
  validateSessionInputs,
} from "@/lib/sessionValidation";
import type {
  ApiBounds,
  ApiNegotiationSession,
  ApiSessionDefaults,
} from "@/lib/types";

interface Props {
  onStart: (session: ApiNegotiationSession) => void;
}

const FRONTEND_FORM_DEFAULTS = {
  strategy: "BASELINE",
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
  },
  maxRounds: 8,
  riskOfWalkaway: 0.15,
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

export function ConfigurationPage({ onStart }: Props) {
  const [error, setError] = useState("");
  const [loadingDefaults, setLoadingDefaults] = useState(true);
  const [startingSession, setStartingSession] = useState(false);
  const [defaults, setDefaults] = useState<ApiSessionDefaults | null>(null);
  const [strategy, setStrategy] = useState(FRONTEND_FORM_DEFAULTS.strategy);
  const [maxPrice, setMaxPrice] = useState(
    String(FRONTEND_FORM_DEFAULTS.buyerProfile.reservationOffer.price),
  );
  const [preferredPrice, setPreferredPrice] = useState(
    String(FRONTEND_FORM_DEFAULTS.buyerProfile.idealOffer.price),
  );
  const [paymentPreferred, setPaymentPreferred] = useState(
    String(FRONTEND_FORM_DEFAULTS.buyerProfile.idealOffer.paymentDays),
  );
  const [paymentMinimum, setPaymentMinimum] = useState(
    String(FRONTEND_FORM_DEFAULTS.buyerProfile.reservationOffer.paymentDays),
  );
  const [deliveryPreferred, setDeliveryPreferred] = useState(
    String(FRONTEND_FORM_DEFAULTS.buyerProfile.idealOffer.deliveryDays),
  );
  const [deliveryMaximum, setDeliveryMaximum] = useState(
    String(FRONTEND_FORM_DEFAULTS.buyerProfile.reservationOffer.deliveryDays),
  );
  const [contractPreferred, setContractPreferred] = useState(
    String(FRONTEND_FORM_DEFAULTS.buyerProfile.idealOffer.contractMonths),
  );
  const [contractMaximum, setContractMaximum] = useState(
    String(FRONTEND_FORM_DEFAULTS.buyerProfile.reservationOffer.contractMonths),
  );
  const [weightPrice, setWeightPrice] = useState(
    String(FRONTEND_FORM_DEFAULTS.buyerProfile.weights.price),
  );
  const [weightPayment, setWeightPayment] = useState(
    String(FRONTEND_FORM_DEFAULTS.buyerProfile.weights.paymentDays),
  );
  const [weightDelivery, setWeightDelivery] = useState(
    String(FRONTEND_FORM_DEFAULTS.buyerProfile.weights.deliveryDays),
  );
  const [weightContract, setWeightContract] = useState(
    String(FRONTEND_FORM_DEFAULTS.buyerProfile.weights.contractMonths),
  );
  const [maxRounds, setMaxRounds] = useState(
    String(FRONTEND_FORM_DEFAULTS.maxRounds),
  );
  const [riskOfWalkaway, setRiskOfWalkaway] = useState(
    String(FRONTEND_FORM_DEFAULTS.riskOfWalkaway),
  );
  const [bounds, setBounds] = useState(() =>
    stringifyBounds(FRONTEND_FORM_DEFAULTS.bounds),
  );
  const selectedStrategy = defaults?.strategyDetails.find(
    (option) => option.name === strategy,
  );

  useEffect(() => {
    let active = true;

    async function loadDefaults() {
      try {
        const response = await fetchNegotiationDefaults();
        if (!active) {
          return;
        }

        setDefaults(response);
        setStrategy((current) =>
          response.availableStrategies.includes(current)
            ? current
            : response.defaultStrategy,
        );

        const reconciledBounds = reconcileFrontendBounds();
        setBounds(stringifyBounds(reconciledBounds));

        const reconciled = reconcileFrontendDefaults(reconciledBounds);
        setPreferredPrice(String(reconciled.idealOffer.price));
        setMaxPrice(String(reconciled.reservationOffer.price));
        setPaymentPreferred(String(reconciled.idealOffer.paymentDays));
        setPaymentMinimum(String(reconciled.reservationOffer.paymentDays));
        setDeliveryPreferred(String(reconciled.idealOffer.deliveryDays));
        setDeliveryMaximum(String(reconciled.reservationOffer.deliveryDays));
        setContractPreferred(String(reconciled.idealOffer.contractMonths));
        setContractMaximum(String(reconciled.reservationOffer.contractMonths));
      } catch (nextError) {
        if (!active) {
          return;
        }
        setError(
          nextError instanceof Error
            ? nextError.message
            : "Could not load negotiation defaults.",
        );
      } finally {
        if (active) {
          setLoadingDefaults(false);
        }
      }
    }

    loadDefaults();

    return () => {
      active = false;
    };
  }, []);

  function parseNumber(value: string) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : NaN;
  }

  async function handleStart() {
    if (!defaults) {
      return;
    }

    const parsed = {
      maxPrice: parseNumber(maxPrice),
      preferredPrice: parseNumber(preferredPrice),
      paymentPreferred: parseNumber(paymentPreferred),
      paymentMinimum: parseNumber(paymentMinimum),
      deliveryPreferred: parseNumber(deliveryPreferred),
      deliveryMaximum: parseNumber(deliveryMaximum),
      contractPreferred: parseNumber(contractPreferred),
      contractMaximum: parseNumber(contractMaximum),
      weights: {
        price: parseNumber(weightPrice),
        paymentDays: parseNumber(weightPayment),
        deliveryDays: parseNumber(weightDelivery),
        contractMonths: parseNumber(weightContract),
      },
      maxRounds: Number.parseInt(maxRounds, 10),
      riskOfWalkaway: parseNumber(riskOfWalkaway),
      bounds: parseBounds(bounds),
    };

    const invalid = Object.entries(parsed).find(
      ([, value]) => typeof value === "number" && Number.isNaN(value as number),
    );

    if (
      invalid ||
      Object.values(parsed.bounds).some((value) => Number.isNaN(value))
    ) {
      setError("Please complete all numeric fields.");
      return;
    }

    const validationError = validateSessionInputs({
      maxRounds: parsed.maxRounds,
      riskOfWalkaway: parsed.riskOfWalkaway,
      idealOffer: {
        price: parsed.preferredPrice,
        paymentDays: parsed.paymentPreferred,
        deliveryDays: parsed.deliveryPreferred,
        contractMonths: parsed.contractPreferred,
      },
      reservationOffer: {
        price: parsed.maxPrice,
        paymentDays: parsed.paymentMinimum,
        deliveryDays: parsed.deliveryMaximum,
        contractMonths: parsed.contractMaximum,
      },
      bounds: parsed.bounds,
    });

    if (validationError) {
      setError(validationError);
      return;
    }

    setError("");
    setStartingSession(true);

    try {
      const idealOffer = {
        price: parsed.preferredPrice,
        paymentDays: parsed.paymentPreferred,
        deliveryDays: parsed.deliveryPreferred,
        contractMonths: parsed.contractPreferred,
      };
      const reservationOffer = {
        price: parsed.maxPrice,
        paymentDays: parsed.paymentMinimum,
        deliveryDays: parsed.deliveryMaximum,
        contractMonths: parsed.contractMaximum,
      };
      const normalizedBounds = normalizeSessionBounds(
        parsed.bounds,
        idealOffer,
        reservationOffer,
      );

      const session = await startNegotiationSession({
        strategy,
        maxRounds: parsed.maxRounds,
        riskOfWalkaway: parsed.riskOfWalkaway,
        buyerProfile: {
          idealOffer,
          reservationOffer,
          weights: parsed.weights,
          reservationUtility: defaults.buyerProfile.reservationUtility,
        },
        bounds: normalizedBounds,
      });

      onStart(session);
    } catch (nextError) {
      setError(
        nextError instanceof Error
          ? nextError.message
          : "Could not start negotiation session.",
      );
    } finally {
      setStartingSession(false);
    }
  }

  if (loadingDefaults) {
    return (
      <div className="grid min-h-screen place-items-center bg-[var(--page-bg)] px-6">
        <div className="rounded-3xl border border-[var(--line)] bg-white/10 px-6 py-5 text-sm font-semibold text-[var(--ink-strong)] shadow-xl shadow-black/10">
          Loading admin setup...
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[var(--page-bg)] px-6 py-8 md:px-12 md:py-12">
      <div className="mx-auto flex max-w-5xl flex-col gap-6">
        <div className="space-y-2">
          <p className="text-sm font-semibold uppercase tracking-[0.2em] text-[var(--accent)]">
            Admin Setup
          </p>
          <h1 className="text-3xl font-extrabold text-[var(--ink-strong)]">
            Configure the buyer bot before session start
          </h1>
          <p className="max-w-3xl text-[var(--ink-soft)]">
            Start every negotiation from this control page. Select the opening
            strategy, set the buyer mandate, and create a real backend
            negotiation session.
          </p>
        </div>

        {error ? (
          <div className="rounded-2xl border border-[var(--danger-ink)] bg-[var(--danger-soft)] px-4 py-3 text-sm font-semibold text-[var(--danger-ink)] shadow-sm shadow-black/5">
            {error}
          </div>
        ) : null}

        <div className="rounded-3xl border border-[var(--line)] bg-white/10 p-6 shadow-xl shadow-black/10 backdrop-blur">
          <div className="space-y-2">
            <p className="text-sm font-semibold text-[var(--ink-strong)]">
              Opening strategy
            </p>
            <p className="text-sm text-[var(--ink-soft)]">
              This form creates the opening backend session and sets the buyer
              mandate in one place.
            </p>
          </div>

          <label className="mt-5 block max-w-md">
            <span className="mb-2 block text-xs font-semibold uppercase tracking-wide text-[var(--ink-muted)]">
              Strategy
            </span>
            <select
              value={strategy}
              onChange={(event) => setStrategy(event.target.value)}
              className="w-full rounded-2xl border border-[var(--line)] bg-[var(--panel)] px-4 py-2.5 text-sm font-semibold text-[var(--ink-strong)] outline-none"
            >
              {defaults?.availableStrategies.map((option) => (
                <option key={option} value={option}>
                  {defaults.strategyDetails.find(
                    (detail) => detail.name === option,
                  )?.label ?? option}
                </option>
              ))}
            </select>
          </label>

          {selectedStrategy ? (
            <div className="mt-4 max-w-2xl rounded-2xl border border-[var(--line)] bg-black/10 px-4 py-3">
              <p className="text-sm font-semibold text-[var(--ink-strong)]">
                {selectedStrategy.label}
              </p>
              <p className="mt-1 text-sm text-[var(--ink-soft)]">
                {selectedStrategy.summary}
              </p>
              <p className="mt-2 text-xs text-[var(--ink-muted)]">
                Concessions: {selectedStrategy.concessionStyle}
              </p>
              <p className="mt-1 text-xs text-[var(--ink-muted)]">
                Boundary posture: {selectedStrategy.boundaryStyle}
              </p>
            </div>
          ) : null}

          <div className="mt-6 border-t border-[var(--line)]/70 pt-6">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--ink-muted)]">
              Buyer mandate
            </p>
            <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
              <FormField
                label="Preferred price"
                prefix="€"
                value={preferredPrice}
                onChange={setPreferredPrice}
                helper="Best target for the buyer."
                step="0.01"
              />
              <FormField
                label="Walk-away price ceiling"
                prefix="€"
                value={maxPrice}
                onChange={setMaxPrice}
                helper="Upper bound the buyer will never cross."
                step="0.01"
              />
              <FormField
                label="Preferred payment days"
                suffix="days"
                value={paymentPreferred}
                onChange={setPaymentPreferred}
                helper="Higher is better for the buyer in the backend model."
                step="1"
              />
              <FormField
                label="Minimum acceptable payment days"
                suffix="days"
                value={paymentMinimum}
                onChange={setPaymentMinimum}
                helper="Anything below is rejected."
                step="1"
              />
              <FormField
                label="Preferred delivery time"
                suffix="days"
                value={deliveryPreferred}
                onChange={setDeliveryPreferred}
                helper="Buyer prefers faster delivery."
                step="1"
              />
              <FormField
                label="Maximum acceptable delivery"
                suffix="days"
                value={deliveryMaximum}
                onChange={setDeliveryMaximum}
                helper="Upper delivery bound the buyer tolerates."
                step="1"
              />
              <FormField
                label="Preferred contract length"
                suffix="months"
                value={contractPreferred}
                onChange={setContractPreferred}
                helper="Target commitment duration."
                step="1"
              />
              <FormField
                label="Maximum acceptable contract"
                suffix="months"
                value={contractMaximum}
                onChange={setContractMaximum}
                helper="Longest contract the buyer will still accept."
                step="1"
              />
              <FormField
                label="Max rounds"
                value={maxRounds}
                onChange={setMaxRounds}
                helper="Agent concedes more as rounds progress."
                step="1"
              />
              <FormField
                label="Risk of walkaway"
                value={riskOfWalkaway}
                onChange={setRiskOfWalkaway}
                helper="A backend input used when evaluating continuation risk."
                step="0.01"
              />
            </div>
          </div>

          <div className="mt-6 border-t border-[var(--line)]/70 pt-6">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--ink-muted)]">
              Negotiation bounds
            </p>
            <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
              <FormField
                label="Minimum price"
                prefix="$"
                value={bounds.minPrice}
                onChange={(value) =>
                  setBounds((current) => ({ ...current, minPrice: value }))
                }
                helper="Absolute lowest price the engine will consider."
                step="0.01"
              />
              <FormField
                label="Maximum price"
                prefix="$"
                value={bounds.maxPrice}
                onChange={(value) =>
                  setBounds((current) => ({ ...current, maxPrice: value }))
                }
                helper="Absolute highest price the engine will consider."
                step="0.01"
              />
              <FormField
                label="Minimum payment days"
                suffix="days"
                value={bounds.minPaymentDays}
                onChange={(value) =>
                  setBounds((current) => ({
                    ...current,
                    minPaymentDays: value,
                  }))
                }
                helper="Lower payment bound for session calculations."
                step="1"
              />
              <FormField
                label="Maximum payment days"
                suffix="days"
                value={bounds.maxPaymentDays}
                onChange={(value) =>
                  setBounds((current) => ({
                    ...current,
                    maxPaymentDays: value,
                  }))
                }
                helper="Upper payment bound for session calculations."
                step="1"
              />
              <FormField
                label="Minimum delivery days"
                suffix="days"
                value={bounds.minDeliveryDays}
                onChange={(value) =>
                  setBounds((current) => ({
                    ...current,
                    minDeliveryDays: value,
                  }))
                }
                helper="Fastest delivery the engine will use in scoring."
                step="1"
              />
              <FormField
                label="Maximum delivery days"
                suffix="days"
                value={bounds.maxDeliveryDays}
                onChange={(value) =>
                  setBounds((current) => ({
                    ...current,
                    maxDeliveryDays: value,
                  }))
                }
                helper="Slowest delivery the engine will use in scoring."
                step="1"
              />
              <FormField
                label="Minimum contract months"
                suffix="months"
                value={bounds.minContractMonths}
                onChange={(value) =>
                  setBounds((current) => ({
                    ...current,
                    minContractMonths: value,
                  }))
                }
                helper="Shortest contract bound for the session."
                step="1"
              />
              <FormField
                label="Maximum contract months"
                suffix="months"
                value={bounds.maxContractMonths}
                onChange={(value) =>
                  setBounds((current) => ({
                    ...current,
                    maxContractMonths: value,
                  }))
                }
                helper="Longest contract bound for the session."
                step="1"
              />
            </div>
          </div>

          <div className="mt-6 border-t border-[var(--line)]/70 pt-6">
            <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[var(--ink-muted)]">
              Issue weights
            </p>
            <div className="mt-4 grid grid-cols-1 gap-4 md:grid-cols-2">
              <FormField
                label="Weight price"
                value={weightPrice}
                onChange={setWeightPrice}
                helper="Higher weight = higher influence."
                step="0.01"
              />
              <FormField
                label="Weight payment"
                value={weightPayment}
                onChange={setWeightPayment}
                helper="Payment term importance."
                step="0.01"
              />
              <FormField
                label="Weight delivery"
                value={weightDelivery}
                onChange={setWeightDelivery}
                helper="Delivery speed importance."
                step="0.01"
              />
              <FormField
                label="Weight contract"
                value={weightContract}
                onChange={setWeightContract}
                helper="Contract term importance."
                step="0.01"
              />
            </div>
          </div>

          <div className="mt-6 flex justify-end">
            <button
              type="button"
              onClick={handleStart}
              disabled={startingSession || !defaults}
              className="rounded-full bg-[var(--accent)] px-6 py-3 text-sm font-semibold uppercase tracking-wide text-white shadow-lg shadow-[var(--accent)]/30 transition hover:-translate-y-0.5 hover:shadow-xl"
            >
              {startingSession ? "Starting session..." : "Start negotiation"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}

function reconcileFrontendBounds() {
  return {
    minPrice: FRONTEND_FORM_DEFAULTS.bounds.minPrice,
    maxPrice: Math.max(
      FRONTEND_FORM_DEFAULTS.bounds.minPrice,
      FRONTEND_FORM_DEFAULTS.bounds.maxPrice,
    ),
    minPaymentDays: FRONTEND_FORM_DEFAULTS.bounds.minPaymentDays,
    maxPaymentDays: Math.max(
      FRONTEND_FORM_DEFAULTS.bounds.minPaymentDays,
      FRONTEND_FORM_DEFAULTS.bounds.maxPaymentDays,
    ),
    minDeliveryDays: FRONTEND_FORM_DEFAULTS.bounds.minDeliveryDays,
    maxDeliveryDays: Math.max(
      FRONTEND_FORM_DEFAULTS.bounds.minDeliveryDays,
      FRONTEND_FORM_DEFAULTS.bounds.maxDeliveryDays,
    ),
    minContractMonths: FRONTEND_FORM_DEFAULTS.bounds.minContractMonths,
    maxContractMonths: Math.max(
      FRONTEND_FORM_DEFAULTS.bounds.minContractMonths,
      FRONTEND_FORM_DEFAULTS.bounds.maxContractMonths,
    ),
  };
}

function reconcileFrontendDefaults(bounds: ApiBounds) {
  const idealOffer = {
    price: clampNumber(
      FRONTEND_FORM_DEFAULTS.buyerProfile.idealOffer.price,
      bounds.minPrice,
      bounds.maxPrice,
    ),
    paymentDays: clampNumber(
      FRONTEND_FORM_DEFAULTS.buyerProfile.idealOffer.paymentDays,
      bounds.minPaymentDays,
      bounds.maxPaymentDays,
    ),
    deliveryDays: clampNumber(
      FRONTEND_FORM_DEFAULTS.buyerProfile.idealOffer.deliveryDays,
      bounds.minDeliveryDays,
      bounds.maxDeliveryDays,
    ),
    contractMonths: clampNumber(
      FRONTEND_FORM_DEFAULTS.buyerProfile.idealOffer.contractMonths,
      bounds.minContractMonths,
      bounds.maxContractMonths,
    ),
  };

  return {
    idealOffer,
    reservationOffer: {
      price: Math.max(
        idealOffer.price,
        clampNumber(
          FRONTEND_FORM_DEFAULTS.buyerProfile.reservationOffer.price,
          bounds.minPrice,
          bounds.maxPrice,
        ),
      ),
      paymentDays: Math.min(
        idealOffer.paymentDays,
        clampNumber(
          FRONTEND_FORM_DEFAULTS.buyerProfile.reservationOffer.paymentDays,
          bounds.minPaymentDays,
          bounds.maxPaymentDays,
        ),
      ),
      deliveryDays: Math.max(
        idealOffer.deliveryDays,
        clampNumber(
          FRONTEND_FORM_DEFAULTS.buyerProfile.reservationOffer.deliveryDays,
          bounds.minDeliveryDays,
          bounds.maxDeliveryDays,
        ),
      ),
      contractMonths: Math.max(
        idealOffer.contractMonths,
        clampNumber(
          FRONTEND_FORM_DEFAULTS.buyerProfile.reservationOffer.contractMonths,
          bounds.minContractMonths,
          bounds.maxContractMonths,
        ),
      ),
    },
  };
}

function clampNumber(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max);
}

function stringifyBounds(bounds: ApiBounds) {
  return {
    minPrice: String(bounds.minPrice),
    maxPrice: String(bounds.maxPrice),
    minPaymentDays: String(bounds.minPaymentDays),
    maxPaymentDays: String(bounds.maxPaymentDays),
    minDeliveryDays: String(bounds.minDeliveryDays),
    maxDeliveryDays: String(bounds.maxDeliveryDays),
    minContractMonths: String(bounds.minContractMonths),
    maxContractMonths: String(bounds.maxContractMonths),
  };
}

function parseBounds(bounds: Record<keyof ApiBounds, string>): ApiBounds {
  return {
    minPrice: Number(bounds.minPrice),
    maxPrice: Number(bounds.maxPrice),
    minPaymentDays: Number(bounds.minPaymentDays),
    maxPaymentDays: Number(bounds.maxPaymentDays),
    minDeliveryDays: Number(bounds.minDeliveryDays),
    maxDeliveryDays: Number(bounds.maxDeliveryDays),
    minContractMonths: Number(bounds.minContractMonths),
    maxContractMonths: Number(bounds.maxContractMonths),
  };
}
