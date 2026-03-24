import { useEffect, useMemo, useRef, useState } from "react";
import {
  CheckCircle2,
  ChevronDown,
  RotateCcw,
  SendHorizontal,
} from "lucide-react";
import { OfferCard } from "@/components/OfferCard";
import {
  parseSupplierOffer,
  submitSupplierOffer,
  updateNegotiationSettings,
} from "@/lib/negotiationApi";
import type {
  ApiConversationEvent,
  ApiNegotiationSession,
  IssueWeights,
  OfferTerms,
} from "@/lib/types";

interface Props {
  initialSession: ApiNegotiationSession;
  onReset: () => void;
  onRestart: () => void;
}

export function NegotiationPage({ initialSession, onReset, onRestart }: Props) {
  const [session, setSession] = useState(initialSession);
  const [supplierMessage, setSupplierMessage] = useState("");
  const [chatError, setChatError] = useState("");
  const [settingsError, setSettingsError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [applyingSettings, setApplyingSettings] = useState(false);
  const [settingsDraft, setSettingsDraft] = useState(() =>
    createSettingsDraft(initialSession),
  );
  const scrollRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    setSession(initialSession);
    setSettingsDraft(createSettingsDraft(initialSession));
    setChatError("");
    setSettingsError("");
  }, [initialSession]);

  useEffect(() => {
    if (!scrollRef.current) {
      return;
    }

    scrollRef.current.scrollTo({
      top: scrollRef.current.scrollHeight,
      behavior: "smooth",
    });
  }, [session.conversation]);

  async function handleSubmit() {
    const message = supplierMessage.trim();
    if (!message) {
      setChatError("Enter a supplier message before sending.");
      return;
    }

    setChatError("");

    try {
      setSubmitting(true);
      const referenceTerms = findReferenceTerms(session);
      const counterOffers = findLatestBuyerCounterOffers(session);
      const parsedOffer = await parseSupplierOffer({
        supplierMessage: message,
        referenceTerms,
        counterOffers,
      });

      const normalizedOffer: OfferTerms = {
        price: parsedOffer.price ?? referenceTerms.price,
        paymentDays: parsedOffer.paymentDays ?? referenceTerms.paymentDays,
        deliveryDays: parsedOffer.deliveryDays ?? referenceTerms.deliveryDays,
        contractMonths:
          parsedOffer.contractMonths ?? referenceTerms.contractMonths,
      };

      const nextSession = await submitSupplierOffer(session.id, {
        ...normalizedOffer,
        supplierMessage: message,
      });

      setSession(nextSession);
      setSupplierMessage("");
    } catch (nextError) {
      setChatError(
        nextError instanceof Error
          ? nextError.message
          : "Could not submit supplier offer.",
      );
    } finally {
      setSubmitting(false);
    }
  }

  function handleDraftChange(field: keyof SettingsDraft, value: string) {
    setSettingsDraft((current) => ({ ...current, [field]: value }));
  }

  function handleNestedDraftChange<T extends keyof SettingsDraft>(
    section: T,
    field: keyof SettingsDraft[T],
    value: string,
  ) {
    setSettingsDraft((current) => ({
      ...current,
      [section]: {
        ...(current[section] as Record<string, string>),
        [field]: value,
      },
    }));
  }

  async function handleApplySettings() {
    const parsed = parseAppliedSettings(settingsDraft);
    if (!parsed) {
      setSettingsError(
        "Complete all settings fields with valid numbers before applying.",
      );
      return;
    }

    setSettingsError("");

    try {
      setApplyingSettings(true);
      const nextSession = await updateNegotiationSettings(session.id, {
        strategy: parsed.strategy,
        maxRounds: parsed.maxRounds,
        riskOfWalkaway: parsed.riskOfWalkaway,
        buyerProfile: {
          idealOffer: parsed.idealOffer,
          reservationOffer: parsed.reservationOffer,
          weights: parsed.weights,
          reservationUtility: session.buyerProfile.reservationUtility,
        },
        bounds: parsed.bounds,
      });

      setSession(nextSession);
      setSettingsDraft(createSettingsDraft(nextSession));
    } catch (nextError) {
      setSettingsError(
        nextError instanceof Error
          ? nextError.message
          : "Could not apply session settings.",
      );
    } finally {
      setApplyingSettings(false);
    }
  }

  const lastBuyerEvent = [...session.conversation]
    .reverse()
    .find((evt) => evt.actor === "buyer");

  const utilityLabel = useMemo(() => {
    const utility = lastBuyerEvent?.debug?.evaluation?.buyerUtility;
    if (utility === undefined || utility === null) {
      return null;
    }
    return `${Math.round(utility * 100)}%`;
  }, [lastBuyerEvent]);

  return (
    <div className='grid h-screen overflow-hidden grid-cols-1 bg-[var(--page-bg)] lg:grid-cols-[minmax(0,1fr)_360px]'>
      <div className='flex min-h-0 flex-col overflow-hidden border-r border-[var(--line)] bg-[rgba(9,24,32,0.72)] px-5 py-5'>
        <header className='flex flex-wrap items-center gap-2 rounded-2xl border border-[var(--line)] bg-black/10 px-4 py-3 shadow-sm shadow-black/10'>
          <StatusChip label='Strategy' value={session.strategy} />
          <StatusChip
            label='Round'
            value={`${session.currentRound}/${session.maxRounds}`}
          />
          <StatusChip label='Status' value={session.status} />
          <StatusChip
            label='Utility'
            value={utilityLabel ?? "n/a"}
            accent={utilityLabel !== null}
          />
        </header>

        <div
          ref={scrollRef}
          className='mt-4 flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto rounded-3xl border border-[var(--line)] bg-black/10 px-4 py-4 shadow-inner shadow-black/10'
        >
          {session.conversation.map((event, index) =>
            event.actor === "system" ? (
              <div
                key={`${event.eventType}-${event.at}-${index}`}
                className='self-center rounded-2xl border border-[var(--line)] bg-[var(--system-soft)] px-4 py-2 text-[12px] text-[var(--system-ink)] shadow-sm shadow-black/10'
              >
                <p className='whitespace-pre-wrap'>{event.message}</p>
              </div>
            ) : (
              <div
                key={`${event.eventType}-${event.at}-${index}`}
                className='flex flex-col gap-2'
              >
                <OfferCard
                  title={event.title}
                  actor={event.actor === "buyer" ? "buyer" : "supplier"}
                  terms={event.terms ?? event.counterOffers[0]}
                  message={event.message}
                  utility={event.debug?.evaluation?.buyerUtility}
                  align={event.actor === "supplier" ? "right" : "left"}
                  highlight={
                    event.eventType === "BUYER_REPLY" &&
                    event.debug?.reasonCode === "TARGET_UTILITY_MET"
                  }
                />
                <MessageDetails event={event} />
              </div>
            ),
          )}

          {session.status === "ACCEPTED" ? (
            <div className='flex items-center gap-2 rounded-2xl border border-[var(--accent)] bg-[var(--accent-soft)] px-3 py-2 text-sm font-semibold text-[var(--accent)] shadow-sm shadow-black/5'>
              <CheckCircle2 className='h-5 w-5' /> Agreement reached.
            </div>
          ) : null}

          {session.status === "REJECTED" ? (
            <div className='rounded-2xl border border-[var(--danger-ink)] bg-[var(--danger-soft)] px-3 py-2 text-sm font-semibold text-[var(--danger-ink)] shadow-sm shadow-black/5'>
              Negotiation rejected. Adjust and try again.
            </div>
          ) : null}
        </div>

        <div className='mt-4 rounded-2xl border border-[var(--line)] bg-[rgba(18,39,49,0.88)] p-3 shadow-sm shadow-black/10'>
          {chatError ? (
            <div className='mb-3 rounded-xl border border-[var(--danger-ink)] bg-[var(--danger-soft)] px-3 py-2 text-sm font-semibold text-[var(--danger-ink)]'>
              {chatError}
            </div>
          ) : null}
          <div className='flex items-end gap-3'>
            <textarea
              className='min-h-[56px] flex-1 resize-none rounded-2xl border border-[var(--line)] bg-black/10 px-4 py-3 text-[13px] leading-6 text-[var(--ink-strong)] outline-none placeholder:text-[var(--ink-soft)]'
              rows={2}
              value={supplierMessage}
              onChange={(event) => setSupplierMessage(event.target.value)}
              placeholder='Write the supplier message here.'
            />
            <button
              type='button'
              onClick={handleSubmit}
              disabled={submitting || session.closed}
              className='flex h-[56px] items-center gap-2 rounded-2xl bg-[var(--accent)] px-5 text-sm font-semibold text-white shadow-lg shadow-[var(--accent)]/30 transition hover:-translate-y-0.5 disabled:cursor-not-allowed disabled:opacity-60'
            >
              {submitting ? "Sending..." : "Send"}
              <SendHorizontal className='h-4 w-4' />
            </button>
          </div>
        </div>
      </div>

      <aside className='flex min-h-0 flex-col overflow-hidden border-l border-[var(--line)] bg-[rgba(18,39,49,0.85)] px-4 py-5 shadow-inner shadow-black/10'>
        <div className='grid grid-cols-2 gap-2'>
          <button
            type='button'
            onClick={handleApplySettings}
            disabled={applyingSettings || session.closed}
            className='rounded-2xl bg-[var(--accent)] px-4 py-3 text-sm font-semibold text-white shadow-lg shadow-[var(--accent)]/25 transition hover:-translate-y-0.5'
          >
            {applyingSettings ? "Applying..." : "Apply"}
          </button>
          <button
            type='button'
            onClick={onRestart}
            className='flex items-center justify-center gap-2 rounded-2xl border border-[var(--line)] bg-black/10 px-4 py-3 text-sm font-semibold text-[var(--ink-strong)] transition hover:-translate-y-0.5'
          >
            <RotateCcw className='h-4 w-4' />
            Restart
          </button>
        </div>

        <div className='mt-4 min-h-0 flex-1 overflow-y-auto pr-1'>
          {settingsError ? (
            <div className='rounded-xl border border-[var(--danger-ink)] bg-[var(--danger-soft)] px-3 py-2 text-sm font-semibold text-[var(--danger-ink)]'>
              {settingsError}
            </div>
          ) : null}

          <div className='rounded-3xl border border-[var(--line)] bg-black/10 p-4'>
            <div className='grid grid-cols-1 gap-3'>
              <SettingsSection title='Session'>
                <CompactSelect
                  label='Strategy'
                  value={settingsDraft.strategy}
                  options={STRATEGIES}
                  onChange={(value) => handleDraftChange("strategy", value)}
                />
                <CompactField
                  label='Max rounds'
                  value={settingsDraft.maxRounds}
                  onChange={(value) => handleDraftChange("maxRounds", value)}
                />
                <CompactField
                  label='Walkaway risk'
                  value={settingsDraft.riskOfWalkaway}
                  onChange={(value) =>
                    handleDraftChange("riskOfWalkaway", value)
                  }
                />
              </SettingsSection>

              <SettingsSection title='Buyer target'>
                <CompactField
                  label='Price'
                  value={settingsDraft.idealOffer.price}
                  onChange={(value) =>
                    handleNestedDraftChange("idealOffer", "price", value)
                  }
                />
                <CompactField
                  label='Payment days'
                  value={settingsDraft.idealOffer.paymentDays}
                  onChange={(value) =>
                    handleNestedDraftChange("idealOffer", "paymentDays", value)
                  }
                />
                <CompactField
                  label='Delivery days'
                  value={settingsDraft.idealOffer.deliveryDays}
                  onChange={(value) =>
                    handleNestedDraftChange("idealOffer", "deliveryDays", value)
                  }
                />
                <CompactField
                  label='Contract months'
                  value={settingsDraft.idealOffer.contractMonths}
                  onChange={(value) =>
                    handleNestedDraftChange(
                      "idealOffer",
                      "contractMonths",
                      value,
                    )
                  }
                />
              </SettingsSection>

              <SettingsSection title='Buyer limits'>
                <CompactField
                  label='Price ceiling'
                  value={settingsDraft.reservationOffer.price}
                  onChange={(value) =>
                    handleNestedDraftChange("reservationOffer", "price", value)
                  }
                />
                <CompactField
                  label='Payment floor'
                  value={settingsDraft.reservationOffer.paymentDays}
                  onChange={(value) =>
                    handleNestedDraftChange(
                      "reservationOffer",
                      "paymentDays",
                      value,
                    )
                  }
                />
                <CompactField
                  label='Delivery ceiling'
                  value={settingsDraft.reservationOffer.deliveryDays}
                  onChange={(value) =>
                    handleNestedDraftChange(
                      "reservationOffer",
                      "deliveryDays",
                      value,
                    )
                  }
                />
                <CompactField
                  label='Contract ceiling'
                  value={settingsDraft.reservationOffer.contractMonths}
                  onChange={(value) =>
                    handleNestedDraftChange(
                      "reservationOffer",
                      "contractMonths",
                      value,
                    )
                  }
                />
              </SettingsSection>

              <SettingsSection title='Bounds'>
                <CompactField
                  label='Min price'
                  value={settingsDraft.bounds.minPrice}
                  onChange={(value) =>
                    handleNestedDraftChange("bounds", "minPrice", value)
                  }
                />
                <CompactField
                  label='Max price'
                  value={settingsDraft.bounds.maxPrice}
                  onChange={(value) =>
                    handleNestedDraftChange("bounds", "maxPrice", value)
                  }
                />
                <CompactField
                  label='Min payment'
                  value={settingsDraft.bounds.minPaymentDays}
                  onChange={(value) =>
                    handleNestedDraftChange("bounds", "minPaymentDays", value)
                  }
                />
                <CompactField
                  label='Max payment'
                  value={settingsDraft.bounds.maxPaymentDays}
                  onChange={(value) =>
                    handleNestedDraftChange("bounds", "maxPaymentDays", value)
                  }
                />
                <CompactField
                  label='Min delivery'
                  value={settingsDraft.bounds.minDeliveryDays}
                  onChange={(value) =>
                    handleNestedDraftChange("bounds", "minDeliveryDays", value)
                  }
                />
                <CompactField
                  label='Max delivery'
                  value={settingsDraft.bounds.maxDeliveryDays}
                  onChange={(value) =>
                    handleNestedDraftChange("bounds", "maxDeliveryDays", value)
                  }
                />
                <CompactField
                  label='Min contract'
                  value={settingsDraft.bounds.minContractMonths}
                  onChange={(value) =>
                    handleNestedDraftChange(
                      "bounds",
                      "minContractMonths",
                      value,
                    )
                  }
                />
                <CompactField
                  label='Max contract'
                  value={settingsDraft.bounds.maxContractMonths}
                  onChange={(value) =>
                    handleNestedDraftChange(
                      "bounds",
                      "maxContractMonths",
                      value,
                    )
                  }
                />
              </SettingsSection>

              <SettingsSection title='Weights'>
                <CompactField
                  label='Price'
                  value={settingsDraft.weights.price}
                  onChange={(value) =>
                    handleNestedDraftChange("weights", "price", value)
                  }
                />
                <CompactField
                  label='Payment'
                  value={settingsDraft.weights.paymentDays}
                  onChange={(value) =>
                    handleNestedDraftChange("weights", "paymentDays", value)
                  }
                />
                <CompactField
                  label='Delivery'
                  value={settingsDraft.weights.deliveryDays}
                  onChange={(value) =>
                    handleNestedDraftChange("weights", "deliveryDays", value)
                  }
                />
                <CompactField
                  label='Contract'
                  value={settingsDraft.weights.contractMonths}
                  onChange={(value) =>
                    handleNestedDraftChange("weights", "contractMonths", value)
                  }
                />
              </SettingsSection>
            </div>
          </div>
        </div>
      </aside>
    </div>
  );
}

const STRATEGIES = ["BASELINE", "MESO", "BOULWARE", "CONCEDER", "TIT_FOR_TAT"];

type SettingsDraft = {
  strategy: string;
  maxRounds: string;
  riskOfWalkaway: string;
  idealOffer: Record<keyof OfferTerms, string>;
  reservationOffer: Record<keyof OfferTerms, string>;
  bounds: {
    minPrice: string;
    maxPrice: string;
    minPaymentDays: string;
    maxPaymentDays: string;
    minDeliveryDays: string;
    maxDeliveryDays: string;
    minContractMonths: string;
    maxContractMonths: string;
  };
  weights: Record<keyof IssueWeights, string>;
};

type AppliedSettings = {
  strategy: string;
  maxRounds: number;
  riskOfWalkaway: number;
  idealOffer: OfferTerms;
  reservationOffer: OfferTerms;
  bounds: ApiNegotiationSession["bounds"];
  weights: IssueWeights;
};

function createSettingsDraft(session: ApiNegotiationSession): SettingsDraft {
  return {
    strategy: session.strategy,
    maxRounds: String(session.maxRounds),
    riskOfWalkaway: String(session.riskOfWalkaway),
    idealOffer: mapTermsToString(session.buyerProfile.idealOffer),
    reservationOffer: mapTermsToString(session.buyerProfile.reservationOffer),
    bounds: {
      minPrice: String(session.bounds.minPrice),
      maxPrice: String(session.bounds.maxPrice),
      minPaymentDays: String(session.bounds.minPaymentDays),
      maxPaymentDays: String(session.bounds.maxPaymentDays),
      minDeliveryDays: String(session.bounds.minDeliveryDays),
      maxDeliveryDays: String(session.bounds.maxDeliveryDays),
      minContractMonths: String(session.bounds.minContractMonths),
      maxContractMonths: String(session.bounds.maxContractMonths),
    },
    weights: {
      price: String(session.buyerProfile.weights.price),
      paymentDays: String(session.buyerProfile.weights.paymentDays),
      deliveryDays: String(session.buyerProfile.weights.deliveryDays),
      contractMonths: String(session.buyerProfile.weights.contractMonths),
    },
  };
}

function mapTermsToString(terms: OfferTerms) {
  return {
    price: String(terms.price),
    paymentDays: String(terms.paymentDays),
    deliveryDays: String(terms.deliveryDays),
    contractMonths: String(terms.contractMonths),
  };
}

function parseAppliedSettings(draft: SettingsDraft): AppliedSettings | null {
  const maxRounds = Number.parseInt(draft.maxRounds, 10);
  const riskOfWalkaway = Number(draft.riskOfWalkaway);
  const idealOffer = parseOfferTerms(draft.idealOffer);
  const reservationOffer = parseOfferTerms(draft.reservationOffer);
  const bounds = {
    minPrice: Number(draft.bounds.minPrice),
    maxPrice: Number(draft.bounds.maxPrice),
    minPaymentDays: Number(draft.bounds.minPaymentDays),
    maxPaymentDays: Number(draft.bounds.maxPaymentDays),
    minDeliveryDays: Number(draft.bounds.minDeliveryDays),
    maxDeliveryDays: Number(draft.bounds.maxDeliveryDays),
    minContractMonths: Number(draft.bounds.minContractMonths),
    maxContractMonths: Number(draft.bounds.maxContractMonths),
  };
  const weights: IssueWeights = {
    price: Number(draft.weights.price),
    paymentDays: Number(draft.weights.paymentDays),
    deliveryDays: Number(draft.weights.deliveryDays),
    contractMonths: Number(draft.weights.contractMonths),
  };

  const validNumbers = [
    maxRounds,
    riskOfWalkaway,
    idealOffer.price,
    idealOffer.paymentDays,
    idealOffer.deliveryDays,
    idealOffer.contractMonths,
    reservationOffer.price,
    reservationOffer.paymentDays,
    reservationOffer.deliveryDays,
    reservationOffer.contractMonths,
    bounds.minPrice,
    bounds.maxPrice,
    bounds.minPaymentDays,
    bounds.maxPaymentDays,
    bounds.minDeliveryDays,
    bounds.maxDeliveryDays,
    bounds.minContractMonths,
    bounds.maxContractMonths,
    weights.price,
    weights.paymentDays,
    weights.deliveryDays,
    weights.contractMonths,
  ];

  if (validNumbers.some((value) => Number.isNaN(value))) {
    return null;
  }

  return {
    strategy: draft.strategy,
    maxRounds,
    riskOfWalkaway,
    idealOffer,
    reservationOffer,
    bounds,
    weights,
  };
}

function parseOfferTerms(terms: Record<keyof OfferTerms, string>): OfferTerms {
  return {
    price: Number(terms.price),
    paymentDays: Number(terms.paymentDays),
    deliveryDays: Number(terms.deliveryDays),
    contractMonths: Number(terms.contractMonths),
  };
}

function findReferenceTerms(session: ApiNegotiationSession): OfferTerms {
  const latestTerms = [...session.conversation]
    .reverse()
    .find((event) => event.terms)?.terms;

  return latestTerms ?? session.buyerProfile.idealOffer;
}

function findLatestBuyerCounterOffers(session: ApiNegotiationSession) {
  return (
    [...session.conversation]
      .reverse()
      .find(
        (event) => event.actor === "buyer" && event.counterOffers.length > 0,
      )?.counterOffers ?? []
  );
}

function StatusChip({
  label,
  value,
  accent = false,
}: {
  label: string;
  value: string;
  accent?: boolean;
}) {
  return (
    <div
      className={`rounded-full border border-[var(--line)] px-3 py-1.5 text-xs font-semibold ${accent ? "bg-[var(--buyer-soft)] text-[var(--buyer-ink)]" : "bg-black/10 text-[var(--ink-strong)]"}`}
    >
      <span className='text-[var(--ink-muted)]'>{label}</span>
      <span className='ml-2'>{value}</span>
    </div>
  );
}

function SettingsSection({
  title,
  children,
}: {
  title: string;
  children: React.ReactNode;
}) {
  return (
    <section className='rounded-2xl border border-[var(--line)] bg-black/10 p-3'>
      <p className='mb-3 text-[11px] font-semibold uppercase tracking-[0.18em] text-[var(--ink-muted)]'>
        {title}
      </p>
      <div className='grid grid-cols-2 gap-3'>{children}</div>
    </section>
  );
}

function CompactField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (next: string) => void;
}) {
  return (
    <label className='flex flex-col gap-1'>
      <span className='text-[11px] font-semibold uppercase tracking-wide text-[var(--ink-muted)]'>
        {label}
      </span>
      <input
        className='rounded-2xl border border-[var(--line)] bg-[var(--panel)]/90 px-3 py-2 text-sm font-semibold text-[var(--ink-strong)] outline-none'
        value={value}
        onChange={(event) => onChange(event.target.value)}
        inputMode='decimal'
      />
    </label>
  );
}

function CompactSelect({
  label,
  value,
  options,
  onChange,
}: {
  label: string;
  value: string;
  options: string[];
  onChange: (next: string) => void;
}) {
  return (
    <label className='col-span-2 flex flex-col gap-1'>
      <span className='text-[11px] font-semibold uppercase tracking-wide text-[var(--ink-muted)]'>
        {label}
      </span>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className='rounded-2xl border border-[var(--line)] bg-[var(--panel)]/90 px-3 py-2 text-sm font-semibold text-[var(--ink-strong)] outline-none'
      >
        {options.map((option) => (
          <option key={option} value={option}>
            {option}
          </option>
        ))}
      </select>
    </label>
  );
}

function MessageDetails({ event }: { event: ApiConversationEvent }) {
  const rows = buildDetailRows(event);

  if (rows.length === 0) {
    return null;
  }

  const alignment = event.actor === "supplier" ? "items-end" : "items-start";

  return (
    <div className={`flex ${alignment}`}>
      <details className='w-full max-w-3xl rounded-2xl border border-[var(--line)] bg-black/10 px-3 py-2 text-[12px] text-[var(--ink-soft)]'>
        <summary className='flex cursor-pointer list-none items-center gap-2 font-semibold text-[var(--ink-muted)]'>
          <ChevronDown className='h-3.5 w-3.5' />
          Details
        </summary>
        <div className='mt-3 grid grid-cols-1 gap-2 md:grid-cols-2'>
          {rows.map((row) => (
            <div
              key={`${row.label}-${row.value}`}
              className='rounded-xl border border-[var(--line)] bg-[var(--panel)]/80 px-3 py-2'
            >
              <p className='text-[11px] font-semibold uppercase tracking-wide text-[var(--ink-muted)]'>
                {row.label}
              </p>
              <p className='mt-1 whitespace-pre-wrap text-[12px] leading-5 text-[var(--ink-strong)]'>
                {row.value}
              </p>
            </div>
          ))}
        </div>
      </details>
    </div>
  );
}

function buildDetailRows(event: ApiConversationEvent) {
  const rows: Array<{ label: string; value: string }> = [];
  const debug = event.debug;

  if (event.terms) {
    rows.push({
      label: "Terms",
      value: formatTerms(event.terms),
    });
  }

  if (debug?.strategy) {
    rows.push({ label: "Strategy used", value: debug.strategy });
  }
  if (debug?.strategyRationale) {
    rows.push({ label: "Strategy rationale", value: debug.strategyRationale });
  }
  if (debug?.reasonCode) {
    rows.push({ label: "Decision reason", value: debug.reasonCode });
  }
  if (debug?.focusIssue) {
    rows.push({ label: "Focus issue", value: debug.focusIssue });
  }
  if (debug?.evaluation) {
    rows.push({
      label: "Evaluation",
      value: [
        `Buyer utility: ${debug.evaluation.buyerUtility}`,
        `Target utility: ${debug.evaluation.targetUtility}`,
        `Estimated supplier utility: ${debug.evaluation.estimatedSupplierUtility}`,
        `Continuation value: ${debug.evaluation.continuationValue}`,
        `Nash product: ${debug.evaluation.nashProduct}`,
      ].join("\n"),
    });
  }
  if (debug?.counterOfferSummary?.length) {
    rows.push({
      label: "Counteroffers",
      value: debug.counterOfferSummary.join("\n"),
    });
  }

  return rows;
}

function formatTerms(terms: OfferTerms) {
  return `Price €${terms.price.toFixed(2)}\nPayment ${terms.paymentDays} days\nDelivery ${terms.deliveryDays} days\nContract ${terms.contractMonths} months`;
}
