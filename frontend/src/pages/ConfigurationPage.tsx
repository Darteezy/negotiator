import { useEffect, useState } from "react";
import { FormField } from "@/components/FormField";
import {
  fetchNegotiationDefaults,
  startNegotiationSession,
} from "@/lib/negotiationApi";
import type { ApiNegotiationSession, ApiSessionDefaults } from "@/lib/types";

interface Props {
  onStart: (session: ApiNegotiationSession) => void;
}

export function ConfigurationPage({ onStart }: Props) {
  const [error, setError] = useState("");
  const [loadingDefaults, setLoadingDefaults] = useState(true);
  const [startingSession, setStartingSession] = useState(false);
  const [defaults, setDefaults] = useState<ApiSessionDefaults | null>(null);
  const [strategy, setStrategy] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
  const [preferredPrice, setPreferredPrice] = useState("");
  const [paymentPreferred, setPaymentPreferred] = useState("");
  const [paymentMinimum, setPaymentMinimum] = useState("");
  const [deliveryPreferred, setDeliveryPreferred] = useState("");
  const [deliveryMaximum, setDeliveryMaximum] = useState("");
  const [contractPreferred, setContractPreferred] = useState("");
  const [contractMaximum, setContractMaximum] = useState("");
  const [weightPrice, setWeightPrice] = useState("0.45");
  const [weightPayment, setWeightPayment] = useState("0.2");
  const [weightDelivery, setWeightDelivery] = useState("0.2");
  const [weightContract, setWeightContract] = useState("0.15");
  const [maxRounds, setMaxRounds] = useState("");
  const [riskOfWalkaway, setRiskOfWalkaway] = useState("");
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
        setStrategy(response.defaultStrategy);
        setPreferredPrice(String(response.buyerProfile.idealOffer.price));
        setMaxPrice(String(response.buyerProfile.reservationOffer.price));
        setPaymentPreferred(
          String(response.buyerProfile.idealOffer.paymentDays),
        );
        setPaymentMinimum(
          String(response.buyerProfile.reservationOffer.paymentDays),
        );
        setDeliveryPreferred(
          String(response.buyerProfile.idealOffer.deliveryDays),
        );
        setDeliveryMaximum(
          String(response.buyerProfile.reservationOffer.deliveryDays),
        );
        setContractPreferred(
          String(response.buyerProfile.idealOffer.contractMonths),
        );
        setContractMaximum(
          String(response.buyerProfile.reservationOffer.contractMonths),
        );
        setWeightPrice(String(response.buyerProfile.weights.price));
        setWeightPayment(String(response.buyerProfile.weights.paymentDays));
        setWeightDelivery(String(response.buyerProfile.weights.deliveryDays));
        setWeightContract(String(response.buyerProfile.weights.contractMonths));
        setMaxRounds(String(response.maxRounds));
        setRiskOfWalkaway(String(response.riskOfWalkaway));
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
    };

    const invalid = Object.entries(parsed).find(
      ([, value]) => typeof value === "number" && Number.isNaN(value as number),
    );

    if (invalid) {
      setError("Please complete all numeric fields.");
      return;
    }

    if (parsed.preferredPrice > parsed.maxPrice) {
      setError(
        "Preferred price must be below or equal to the walk-away ceiling.",
      );
      return;
    }

    if (parsed.paymentPreferred < parsed.paymentMinimum) {
      setError(
        "Preferred payment days must be greater than or equal to the minimum acceptable payment days.",
      );
      return;
    }

    if (parsed.deliveryPreferred > parsed.deliveryMaximum) {
      setError(
        "Preferred delivery must be faster than or equal to the maximum acceptable delivery.",
      );
      return;
    }

    if (parsed.contractPreferred > parsed.contractMaximum) {
      setError(
        "Preferred contract length must be shorter than or equal to the maximum acceptable contract length.",
      );
      return;
    }

    setError("");
    setStartingSession(true);

    try {
      const session = await startNegotiationSession({
        strategy,
        maxRounds: parsed.maxRounds,
        riskOfWalkaway: parsed.riskOfWalkaway,
        buyerProfile: {
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
          weights: parsed.weights,
          reservationUtility: defaults.buyerProfile.reservationUtility,
        },
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
      <div className='grid min-h-screen place-items-center bg-[var(--page-bg)] px-6'>
        <div className='rounded-3xl border border-[var(--line)] bg-white/10 px-6 py-5 text-sm font-semibold text-[var(--ink-strong)] shadow-xl shadow-black/10'>
          Loading admin setup...
        </div>
      </div>
    );
  }

  return (
    <div className='min-h-screen bg-[var(--page-bg)] px-6 py-8 md:px-12 md:py-12'>
      <div className='mx-auto flex max-w-5xl flex-col gap-6'>
        <div className='space-y-2'>
          <p className='text-sm font-semibold uppercase tracking-[0.2em] text-[var(--accent)]'>
            Admin Setup
          </p>
          <h1 className='text-3xl font-extrabold text-[var(--ink-strong)]'>
            Configure the buyer bot before session start
          </h1>
          <p className='max-w-3xl text-[var(--ink-soft)]'>
            Start every negotiation from this control page. Select the opening
            strategy, set the buyer mandate, and create a real backend
            negotiation session.
          </p>
        </div>

        {error ? (
          <div className='rounded-2xl border border-[var(--danger-ink)] bg-[var(--danger-soft)] px-4 py-3 text-sm font-semibold text-[var(--danger-ink)] shadow-sm shadow-black/5'>
            {error}
          </div>
        ) : null}

        <div className='rounded-3xl border border-[var(--line)] bg-white/10 p-6 shadow-xl shadow-black/10 backdrop-blur'>
          <div className='space-y-2'>
            <p className='text-sm font-semibold text-[var(--ink-strong)]'>
              Opening strategy
            </p>
            <p className='text-sm text-[var(--ink-soft)]'>
              This form creates the opening backend session and sets the buyer
              mandate in one place.
            </p>
          </div>

          <label className='mt-5 block max-w-md'>
            <span className='mb-2 block text-xs font-semibold uppercase tracking-wide text-[var(--ink-muted)]'>
              Strategy
            </span>
            <select
              value={strategy}
              onChange={(event) => setStrategy(event.target.value)}
              className='w-full rounded-2xl border border-[var(--line)] bg-[var(--panel)] px-4 py-2.5 text-sm font-semibold text-[var(--ink-strong)] outline-none'
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
            <div className='mt-4 max-w-2xl rounded-2xl border border-[var(--line)] bg-black/10 px-4 py-3'>
              <p className='text-sm font-semibold text-[var(--ink-strong)]'>
                {selectedStrategy.label}
              </p>
              <p className='mt-1 text-sm text-[var(--ink-soft)]'>
                {selectedStrategy.summary}
              </p>
              <p className='mt-2 text-xs text-[var(--ink-muted)]'>
                Concessions: {selectedStrategy.concessionStyle}
              </p>
              <p className='mt-1 text-xs text-[var(--ink-muted)]'>
                Boundary posture: {selectedStrategy.boundaryStyle}
              </p>
            </div>
          ) : null}

          <div className='mt-6 border-t border-[var(--line)]/70 pt-6'>
            <p className='text-xs font-semibold uppercase tracking-[0.18em] text-[var(--ink-muted)]'>
              Buyer mandate
            </p>
            <div className='mt-4 grid grid-cols-1 gap-4 md:grid-cols-2'>
              <FormField
                label='Preferred price'
                prefix='€'
                value={preferredPrice}
                onChange={setPreferredPrice}
                helper='Best target for the buyer.'
                step='0.01'
              />
              <FormField
                label='Walk-away price ceiling'
                prefix='€'
                value={maxPrice}
                onChange={setMaxPrice}
                helper='Upper bound the buyer will never cross.'
                step='0.01'
              />
              <FormField
                label='Preferred payment days'
                suffix='days'
                value={paymentPreferred}
                onChange={setPaymentPreferred}
                helper='Higher is better for the buyer in the backend model.'
                step='1'
              />
              <FormField
                label='Minimum acceptable payment days'
                suffix='days'
                value={paymentMinimum}
                onChange={setPaymentMinimum}
                helper='Anything below is rejected.'
                step='1'
              />
              <FormField
                label='Preferred delivery time'
                suffix='days'
                value={deliveryPreferred}
                onChange={setDeliveryPreferred}
                helper='Buyer prefers faster delivery.'
                step='1'
              />
              <FormField
                label='Maximum acceptable delivery'
                suffix='days'
                value={deliveryMaximum}
                onChange={setDeliveryMaximum}
                helper='Upper delivery bound the buyer tolerates.'
                step='1'
              />
              <FormField
                label='Preferred contract length'
                suffix='months'
                value={contractPreferred}
                onChange={setContractPreferred}
                helper='Target commitment duration.'
                step='1'
              />
              <FormField
                label='Maximum acceptable contract'
                suffix='months'
                value={contractMaximum}
                onChange={setContractMaximum}
                helper='Longest contract the buyer will still accept.'
                step='1'
              />
              <FormField
                label='Max rounds'
                value={maxRounds}
                onChange={setMaxRounds}
                helper='Agent concedes more as rounds progress.'
                step='1'
              />
              <FormField
                label='Risk of walkaway'
                value={riskOfWalkaway}
                onChange={setRiskOfWalkaway}
                helper='A backend input used when evaluating continuation risk.'
                step='0.01'
              />
            </div>
          </div>

          <div className='mt-6 border-t border-[var(--line)]/70 pt-6'>
            <p className='text-xs font-semibold uppercase tracking-[0.18em] text-[var(--ink-muted)]'>
              Issue weights
            </p>
            <div className='mt-4 grid grid-cols-1 gap-4 md:grid-cols-2'>
              <FormField
                label='Weight price'
                value={weightPrice}
                onChange={setWeightPrice}
                helper='Higher weight = higher influence.'
                step='0.01'
              />
              <FormField
                label='Weight payment'
                value={weightPayment}
                onChange={setWeightPayment}
                helper='Payment term importance.'
                step='0.01'
              />
              <FormField
                label='Weight delivery'
                value={weightDelivery}
                onChange={setWeightDelivery}
                helper='Delivery speed importance.'
                step='0.01'
              />
              <FormField
                label='Weight contract'
                value={weightContract}
                onChange={setWeightContract}
                helper='Contract term importance.'
                step='0.01'
              />
            </div>
          </div>

          <div className='mt-6 flex justify-end'>
            <button
              type='button'
              onClick={handleStart}
              disabled={startingSession || !defaults}
              className='rounded-full bg-[var(--accent)] px-6 py-3 text-sm font-semibold uppercase tracking-wide text-white shadow-lg shadow-[var(--accent)]/30 transition hover:-translate-y-0.5 hover:shadow-xl'
            >
              {startingSession ? "Starting session..." : "Start negotiation"}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
