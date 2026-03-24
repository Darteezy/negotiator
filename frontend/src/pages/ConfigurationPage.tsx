import { useState } from "react";
import { FormField } from "@/components/FormField";
import type { BuyerPreferences } from "@/lib/types";

interface Props {
  onStart: (profile: BuyerPreferences) => void;
}

export function ConfigurationPage({ onStart }: Props) {
  const [error, setError] = useState("");
  const [maxPrice, setMaxPrice] = useState("12000");
  const [preferredPrice, setPreferredPrice] = useState("10000");
  const [paymentPreferred, setPaymentPreferred] = useState("30");
  const [paymentMax, setPaymentMax] = useState("60");
  const [deliveryPreferred, setDeliveryPreferred] = useState("10");
  const [deliveryMax, setDeliveryMax] = useState("30");
  const [contractPreferred, setContractPreferred] = useState("12");
  const [contractMin, setContractMin] = useState("6");
  const [contractMax, setContractMax] = useState("18");
  const [weightPrice, setWeightPrice] = useState("0.45");
  const [weightPayment, setWeightPayment] = useState("0.2");
  const [weightDelivery, setWeightDelivery] = useState("0.2");
  const [weightContract, setWeightContract] = useState("0.15");
  const [maxRounds, setMaxRounds] = useState("8");

  function parseNumber(value: string) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : NaN;
  }

  function handleStart() {
    const parsed = {
      maxPrice: parseNumber(maxPrice),
      preferredPrice: parseNumber(preferredPrice),
      paymentPreferred: parseNumber(paymentPreferred),
      paymentMax: parseNumber(paymentMax),
      deliveryPreferred: parseNumber(deliveryPreferred),
      deliveryMax: parseNumber(deliveryMax),
      contractPreferred: parseNumber(contractPreferred),
      contractMin: parseNumber(contractMin),
      contractMax: parseNumber(contractMax),
      weights: {
        price: parseNumber(weightPrice),
        paymentDays: parseNumber(weightPayment),
        deliveryDays: parseNumber(weightDelivery),
        contractMonths: parseNumber(weightContract),
      },
      maxRounds: Number.parseInt(maxRounds, 10),
    };

    const invalid = Object.entries(parsed).find(([, value]) =>
      typeof value === "number" && Number.isNaN(value as number),
    );

    if (invalid) {
      setError("Please complete all numeric fields.");
      return;
    }

    if (parsed.preferredPrice > parsed.maxPrice) {
      setError("Preferred price must be below or equal to max price.");
      return;
    }

    if (parsed.paymentPreferred > parsed.paymentMax) {
      setError("Preferred payment days cannot exceed max acceptable payment days.");
      return;
    }

    if (parsed.deliveryPreferred > parsed.deliveryMax) {
      setError("Preferred delivery must be faster than or equal to the maximum acceptable delivery.");
      return;
    }

    if (
      parsed.contractPreferred < parsed.contractMin ||
      parsed.contractPreferred > parsed.contractMax
    ) {
      setError("Preferred contract length must sit inside the acceptable range.");
      return;
    }

    const profile: BuyerPreferences = {
      price: {
        preferred: parsed.preferredPrice,
        max: parsed.maxPrice,
      },
      paymentDays: {
        preferred: parsed.paymentPreferred,
        max: parsed.paymentMax,
      },
      deliveryDays: {
        preferred: parsed.deliveryPreferred,
        max: parsed.deliveryMax,
      },
      contractMonths: {
        preferred: parsed.contractPreferred,
        min: parsed.contractMin,
        max: parsed.contractMax,
      },
      weights: parsed.weights,
      thresholds: {
        acceptStart: 0.8,
        acceptFloor: 0.62,
        counterStart: 0.45,
        counterFloor: 0.35,
      },
      maxRounds: parsed.maxRounds || 8,
    };

    setError("");
    onStart(profile);
  }

  return (
    <div className='grid min-h-screen grid-cols-1 bg-[var(--page-bg)] px-6 py-8 md:grid-cols-2 md:px-12 md:py-12'>
      <div className='flex flex-col justify-center gap-6'>
        <div className='space-y-2'>
          <p className='text-sm font-semibold uppercase tracking-[0.2em] text-[var(--accent)]'>
            Autonomous Negotiation Agent
          </p>
          <h1 className='text-3xl font-extrabold text-[var(--ink-strong)]'>
            Configure buyer intent
          </h1>
          <p className='max-w-xl text-[var(--ink-soft)]'>
            Set the buyer mandate and weightings. The agent will compute utility, generate MESO counteroffers, and stay within limits while conceding over time.
          </p>
        </div>
        {error ? (
          <div className='rounded-2xl border border-[var(--danger-ink)] bg-[var(--danger-soft)] px-4 py-3 text-sm font-semibold text-[var(--danger-ink)] shadow-sm shadow-black/5'>
            {error}
          </div>
        ) : null}
      </div>
      <div className='rounded-3xl border border-[var(--line)] bg-white/85 p-6 shadow-xl shadow-black/5 backdrop-blur'>
        <div className='grid grid-cols-2 gap-4'>
          <FormField
            label='Preferred price'
            prefix='€'
            value={preferredPrice}
            onChange={setPreferredPrice}
            helper='Best target for the buyer.'
            step='0.01'
          />
          <FormField
            label='Max price'
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
            helper='Lower is better for the buyer in this model.'
            step='1'
          />
          <FormField
            label='Max acceptable payment days'
            suffix='days'
            value={paymentMax}
            onChange={setPaymentMax}
            helper='Anything above is rejected.'
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
            label='Max acceptable delivery'
            suffix='days'
            value={deliveryMax}
            onChange={setDeliveryMax}
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
            label='Contract range min'
            suffix='months'
            value={contractMin}
            onChange={setContractMin}
            helper='Shortest acceptable contract.'
            step='1'
          />
          <FormField
            label='Contract range max'
            suffix='months'
            value={contractMax}
            onChange={setContractMax}
            helper='Longest acceptable contract.'
            step='1'
          />
          <FormField
            label='Max rounds'
            value={maxRounds}
            onChange={setMaxRounds}
            helper='Agent concedes more as rounds progress.'
            step='1'
          />
        </div>
        <div className='mt-6 grid grid-cols-4 gap-3'>
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
        <div className='mt-6 flex justify-end'>
          <button
            type='button'
            onClick={handleStart}
            className='rounded-full bg-[var(--accent)] px-6 py-3 text-sm font-semibold uppercase tracking-wide text-white shadow-lg shadow-[var(--accent)]/30 transition hover:-translate-y-0.5 hover:shadow-xl'
          >
            Start negotiation
          </button>
        </div>
      </div>
    </div>
  );
}
