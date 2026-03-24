import { useMemo, useRef, useState } from "react";
import { ArrowLeft, ArrowRight, CheckCircle2, RotateCcw } from "lucide-react";
import { HistoryLog } from "@/components/HistoryLog";
import { OfferCard } from "@/components/OfferCard";
import {
  processSupplierOffer,
  startNegotiation,
} from "@/lib/negotiationEngine";
import {
  BuyerPreferences,
  NegotiationEvent,
  OfferTerms,
  SupplierOfferInput,
} from "@/lib/types";
import { computeUtility, describeUtilityLevel } from "@/lib/utilityFunction";

interface Props {
  profile: BuyerPreferences;
  onReset: () => void;
  onRestart: () => void;
}

export function NegotiationPage({ profile, onReset, onRestart }: Props) {
  const [state, setState] = useState(() => startNegotiation(profile));
  const [price, setPrice] = useState(String(profile.price.preferred));
  const [payment, setPayment] = useState(String(profile.paymentDays.preferred));
  const [delivery, setDelivery] = useState(String(profile.deliveryDays.preferred));
  const [contract, setContract] = useState(String(profile.contractMonths.preferred));
  const [note, setNote] = useState("I am the supplier. Here is my offer.");
  const [error, setError] = useState("");
  const scrollRef = useRef<HTMLDivElement | null>(null);

  const lastUtility = useMemo(() => {
    const lastEvent = [...state.history].reverse().find((e) => e.utility !== undefined);
    return lastEvent?.utility ?? null;
  }, [state.history]);

  const supplierForm: OfferTerms | null = useMemo(() => {
    const parsed = {
      price: Number(price),
      paymentDays: Math.round(Number(payment)),
      deliveryDays: Math.round(Number(delivery)),
      contractMonths: Math.round(Number(contract)),
    } as OfferTerms;

    const anyNaN = Object.values(parsed).some((v) => Number.isNaN(v));
    return anyNaN ? null : parsed;
  }, [price, payment, delivery, contract]);

  const utilityPreview = supplierForm ? computeUtility(supplierForm, profile) : null;

  function resetFieldsFromTerms(terms: OfferTerms) {
    setPrice(String(terms.price));
    setPayment(String(terms.paymentDays));
    setDelivery(String(terms.deliveryDays));
    setContract(String(terms.contractMonths));
  }

  function handleSubmit() {
    if (!supplierForm) {
      setError("Please fill every numeric field.");
      return;
    }

    setError("");

    const payload: SupplierOfferInput = {
      terms: supplierForm,
      note,
    };

    const next = processSupplierOffer(state, profile, payload);
    setState(next);
    setNote("Following up on the buyer feedback.");
    if (scrollRef.current) {
      scrollRef.current.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
    }
  }

  function handleAcceptCounter(counterTerms: OfferTerms) {
    resetFieldsFromTerms(counterTerms);
    setNote("Accepting your counteroffer.");
  }

  function headerSummary(event?: NegotiationEvent) {
    if (!event?.utility) {
      return "Agent will concede gradually but stays within limits.";
    }
    return `Latest utility ${Math.round(event.utility * 100)}% (${describeUtilityLevel(event.utility)}).`;
  }

  const lastBuyerEvent = [...state.history]
    .reverse()
    .find((evt) => evt.actor === "buyer");

  return (
    <div className='grid min-h-screen grid-cols-1 bg-[var(--page-bg)] lg:grid-cols-[1fr_420px]'>
      <div className='flex min-h-0 flex-col border-r border-[var(--line)] bg-gradient-to-b from-white/70 via-white/60 to-white/40 px-6 py-6'>
        <header className='flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-[var(--line)] bg-white/80 px-4 py-3 shadow-sm shadow-black/5'>
          <div>
            <p className='text-xs font-semibold uppercase tracking-[0.2em] text-[var(--accent)]'>
              Buyer agent live
            </p>
            <h2 className='text-xl font-extrabold text-[var(--ink-strong)]'>Negotiation chat</h2>
            <p className='text-sm text-[var(--ink-soft)]'>{headerSummary(lastBuyerEvent)}</p>
          </div>
          <div className='flex items-center gap-2'>
            <button
              type='button'
              onClick={onReset}
              className='flex items-center gap-1 rounded-full border border-[var(--line)] bg-white px-3 py-2 text-xs font-semibold uppercase tracking-wide text-[var(--ink-strong)] shadow-sm shadow-black/5 hover:-translate-y-0.5 transition'
            >
              <ArrowLeft className='h-4 w-4' />
              Reconfigure
            </button>
            <button
              type='button'
              onClick={onRestart}
              className='flex items-center gap-1 rounded-full bg-[var(--accent)] px-3 py-2 text-xs font-semibold uppercase tracking-wide text-white shadow-sm shadow-[var(--accent)]/30 hover:-translate-y-0.5 transition'
            >
              <RotateCcw className='h-4 w-4' />
              Restart
            </button>
          </div>
        </header>

        <div className='mt-4 grid grid-cols-2 gap-3 rounded-2xl border border-[var(--line)] bg-white/80 p-4 shadow-sm shadow-black/5'>
          <Metric label='Price' value={`€${profile.price.preferred.toFixed(2)} (max €${profile.price.max.toFixed(2)})`} accent />
          <Metric label='Payment' value={`${profile.paymentDays.preferred}d (max ${profile.paymentDays.max}d)`} />
          <Metric label='Delivery' value={`${profile.deliveryDays.preferred}d (max ${profile.deliveryDays.max}d)`} />
          <Metric label='Contract' value={`${profile.contractMonths.preferred}m (range ${profile.contractMonths.min}-${profile.contractMonths.max}m)`} />
        </div>

        <div
          ref={scrollRef}
          className='mt-4 flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto rounded-3xl border border-[var(--line)] bg-white/75 px-4 py-5 shadow-inner shadow-black/5'
        >
          {state.history.map((event) => (
            <OfferCard
              key={event.id}
              title={event.title}
              actor={event.actor === "buyer" ? "buyer" : "supplier"}
              terms={event.terms}
              message={event.message}
              utility={event.utility}
              breakdown={event.breakdown}
              align={event.actor === "supplier" ? "right" : "left"}
              highlight={event.decision === "ACCEPT"}
              mesoLabel={event.mesoGroup}
            />
          ))}

          {state.status === "accepted" ? (
            <div className='flex items-center gap-2 rounded-2xl border border-[var(--accent)] bg-[var(--accent-soft)] px-3 py-2 text-sm font-semibold text-[var(--accent)] shadow-sm shadow-black/5'>
              <CheckCircle2 className='h-5 w-5' /> Agreement reached. 🎉
            </div>
          ) : null}

          {state.status === "rejected" ? (
            <div className='rounded-2xl border border-[var(--danger-ink)] bg-[var(--danger-soft)] px-3 py-2 text-sm font-semibold text-[var(--danger-ink)] shadow-sm shadow-black/5'>
              Negotiation rejected. Adjust and try again.
            </div>
          ) : null}
        </div>

        <div className='mt-4 rounded-2xl border border-[var(--line)] bg-white/85 p-4 shadow-sm shadow-black/5'>
          <div className='flex flex-wrap items-center justify-between gap-3'>
            <div>
              <p className='text-xs font-semibold uppercase tracking-wide text-[var(--ink-muted)]'>
                Supplier offer
              </p>
              <p className='text-sm text-[var(--ink-soft)]'>
                Structured terms keep the chat symmetric. Utility preview updates live.
              </p>
            </div>
            {utilityPreview ? (
              <div className='flex items-center gap-2 rounded-full bg-[var(--buyer-soft)] px-3 py-1 text-xs font-semibold text-[var(--buyer-ink)] shadow-sm shadow-black/5'>
                Utility {Math.round(utilityPreview.utility * 100)}% · {describeUtilityLevel(utilityPreview.utility)}
              </div>
            ) : null}
          </div>
          {error ? (
            <div className='mt-3 rounded-xl border border-[var(--danger-ink)] bg-[var(--danger-soft)] px-3 py-2 text-sm font-semibold text-[var(--danger-ink)]'>
              {error}
            </div>
          ) : null}
          <div className='mt-4 grid grid-cols-4 gap-3'>
            <InputPill label='Price' prefix='€' value={price} onChange={setPrice} />
            <InputPill label='Payment days' suffix='d' value={payment} onChange={setPayment} />
            <InputPill label='Delivery days' suffix='d' value={delivery} onChange={setDelivery} />
            <InputPill label='Contract months' suffix='m' value={contract} onChange={setContract} />
          </div>
          <textarea
            className='mt-3 w-full rounded-2xl border border-[var(--line)] bg-[var(--panel)] px-4 py-3 text-sm text-[var(--ink-strong)] shadow-inner shadow-black/5 outline-none'
            rows={3}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder='Add a short negotiation note for the buyer agent.'
          />
          <div className='mt-3 flex flex-wrap items-center justify-between gap-2'>
            <div className='flex flex-wrap gap-2'>
              {lastBuyerEvent?.counterOffers?.map((offer, index) => (
                <button
                  key={index}
                  type='button'
                  onClick={() => handleAcceptCounter(offer)}
                  className='rounded-full border border-[var(--line)] bg-white px-3 py-2 text-xs font-semibold text-[var(--ink-strong)] shadow-sm shadow-black/5 hover:bg-[var(--buyer-soft)]'
                >
                  Accept option {index + 1}
                </button>
              ))}
            </div>
            <button
              type='button'
              onClick={handleSubmit}
              className='flex items-center gap-2 rounded-full bg-[var(--accent)] px-5 py-3 text-sm font-bold uppercase tracking-wide text-white shadow-lg shadow-[var(--accent)]/30 transition hover:-translate-y-0.5'
            >
              Send offer
              <ArrowRight className='h-4 w-4' />
            </button>
          </div>
        </div>
      </div>

      <aside className='flex min-h-0 flex-col gap-4 bg-white/80 px-5 py-6 shadow-inner shadow-black/5'>
        <div className='rounded-2xl border border-[var(--line)] bg-white/90 p-4 shadow-sm shadow-black/5'>
          <p className='text-xs font-semibold uppercase tracking-wide text-[var(--ink-muted)]'>
            Buyer thresholds
          </p>
          <div className='mt-3 grid grid-cols-2 gap-2 text-sm'>
            <BadgeRow label='Accept start' value={`${Math.round(profile.thresholds.acceptStart * 100)}%`} />
            <BadgeRow label='Accept floor' value={`${Math.round(profile.thresholds.acceptFloor * 100)}%`} />
            <BadgeRow label='Counter start' value={`${Math.round(profile.thresholds.counterStart * 100)}%`} />
            <BadgeRow label='Counter floor' value={`${Math.round(profile.thresholds.counterFloor * 100)}%`} />
            <BadgeRow label='Max rounds' value={String(profile.maxRounds)} />
          </div>
        </div>
        <HistoryLog events={state.history} />
      </aside>
    </div>
  );
}

function Metric({ label, value, accent = false }: { label: string; value: string; accent?: boolean }) {
  return (
    <div className={`rounded-2xl px-4 py-3 shadow-inner shadow-black/5 ${accent ? "bg-[var(--buyer-soft)]" : "bg-[var(--panel)]"} border border-[var(--line)]`}>
      <p className='text-[11px] font-semibold uppercase tracking-wide text-[var(--ink-muted)]'>{label}</p>
      <p className='text-sm font-semibold text-[var(--ink-strong)]'>{value}</p>
    </div>
  );
}

function InputPill({
  label,
  value,
  onChange,
  prefix,
  suffix,
}: {
  label: string;
  value: string;
  onChange: (next: string) => void;
  prefix?: string;
  suffix?: string;
}) {
  return (
    <label className='flex flex-col gap-1 rounded-2xl border border-[var(--line)] bg-white px-3 py-2 shadow-inner shadow-black/5'>
      <span className='text-xs font-semibold uppercase tracking-wide text-[var(--ink-muted)]'>
        {label}
      </span>
      <div className='flex items-center gap-2 rounded-xl bg-[var(--panel)] px-3 py-2 ring-1 ring-[var(--line)]'>
        {prefix ? <span className='text-xs font-semibold text-[var(--ink-soft)]'>{prefix}</span> : null}
        <input
          className='w-full border-none bg-transparent text-sm font-semibold text-[var(--ink-strong)] outline-none'
          value={value}
          onChange={(e) => onChange(e.target.value)}
          inputMode='decimal'
        />
        {suffix ? <span className='text-xs font-semibold text-[var(--ink-soft)]'>{suffix}</span> : null}
      </div>
    </label>
  );
}

function BadgeRow({ label, value }: { label: string; value: string }) {
  return (
    <div className='flex items-center justify-between rounded-xl bg-[var(--panel)] px-3 py-2 text-xs font-semibold text-[var(--ink-strong)] shadow-inner shadow-black/5 ring-1 ring-[var(--line)]'>
      <span className='text-[var(--ink-muted)]'>{label}</span>
      <span>{value}</span>
    </div>
  );
}
