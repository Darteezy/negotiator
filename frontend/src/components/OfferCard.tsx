import botAvatar from "@/assets/robot.svg";
import type { UtilityBreakdown } from "@/lib/types";
import type { OfferTerms } from "@/lib/types";

interface Props {
  title: string;
  actor: "buyer" | "supplier";
  terms?: OfferTerms;
  message?: string;
  utility?: number;
  breakdown?: UtilityBreakdown;
  align?: "left" | "right";
  highlight?: boolean;
  mesoLabel?: string;
}

export function OfferCard({
  title,
  actor,
  terms,
  message,
  utility,
  breakdown,
  align = "left",
  highlight = false,
  mesoLabel,
}: Props) {
  const isBuyer = actor === "buyer";
  const alignment = align === "right" ? "items-end text-right" : "items-start text-left";
  const bubble = isBuyer
    ? "bg-[var(--buyer-soft)] text-[var(--buyer-ink)]"
    : "bg-[var(--supplier-soft)] text-[var(--supplier-ink)]";

  return (
    <div className={`flex flex-col gap-2 ${alignment}`}>
      <div className='flex items-center gap-2 text-xs uppercase tracking-wide text-[var(--ink-muted)]'>
        {isBuyer ? (
          <img
            src={botAvatar}
            alt='Buyer bot'
            className='h-8 w-8 rounded-full border border-[var(--line)] bg-white shadow-sm shadow-black/10'
          />
        ) : (
          <span className='h-8 w-8 rounded-full bg-white shadow-sm shadow-black/5 ring-1 ring-[var(--line)] flex items-center justify-center font-semibold text-[var(--ink-strong)]'>
            S
          </span>
        )}
        <div className='flex flex-col'>
          <span className='font-semibold text-[var(--ink-strong)]'>{title}</span>
          {mesoLabel ? (
            <span className='text-[11px] font-semibold text-[var(--ink-soft)]'>
              {mesoLabel}
            </span>
          ) : null}
        </div>
      </div>
      <div
        className={`w-full max-w-2xl rounded-2xl border border-[var(--line)] px-4 py-3 shadow-sm shadow-black/5 ${bubble} ${highlight ? "ring-2 ring-[var(--accent)]" : ""}`}
      >
        {message ? (
          <p className='mb-2 text-sm leading-relaxed whitespace-pre-wrap'>{message}</p>
        ) : null}
        {terms ? (
          <div className='grid grid-cols-2 gap-3 text-sm font-semibold'>
            <TermPill label='Price' value={`€${terms.price.toFixed(2)}`} />
            <TermPill label='Payment' value={`${terms.paymentDays} days`} />
            <TermPill label='Delivery' value={`${terms.deliveryDays} days`} />
            <TermPill label='Contract' value={`${terms.contractMonths} months`} />
          </div>
        ) : null}
        {utility !== undefined ? (
          <div className='mt-3 flex flex-wrap items-center gap-2 text-xs text-[var(--ink-soft)]'>
            <span className='rounded-full bg-white/70 px-3 py-1 font-semibold text-[var(--ink-strong)] shadow-sm shadow-black/5'>
              Utility {Math.round(utility * 100)}%
            </span>
            {breakdown
              ? Object.entries(breakdown).map(([key, part]) => (
                  <span
                    key={key}
                    className='rounded-full bg-black/5 px-2.5 py-1 font-semibold text-[11px] uppercase text-[var(--ink-soft)]'
                  >
                    {key} {Math.round(part.score * 100)}%
                  </span>
                ))
              : null}
          </div>
        ) : null}
      </div>
    </div>
  );
}

function TermPill({ label, value }: { label: string; value: string }) {
  return (
    <div className='flex flex-col rounded-xl bg-white/70 px-3 py-2 text-left shadow-inner shadow-black/5 ring-1 ring-[var(--line)]'>
      <span className='text-[11px] font-semibold uppercase tracking-wide text-[var(--ink-muted)]'>
        {label}
      </span>
      <span className='text-base text-[var(--ink-strong)]'>{value}</span>
    </div>
  );
}
