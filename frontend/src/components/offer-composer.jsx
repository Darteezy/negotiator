import { LoaderCircle, SendHorizontal } from "lucide-react";

import { formatMoney } from "@/lib/format";
import { Button } from "@/components/ui/button";
import {
  formatTermRange,
  getSessionTermField,
  SESSION_TERM_FIELDS,
} from "@/lib/session-config";

export function OfferComposer({
  bounds,
  disabled,
  draft,
  error,
  onChange,
  onSubmit,
  parsedDraft,
  session,
  submittingOffer,
}) {
  return (
    <form
      className='shrink-0 border-t border-[var(--line)] bg-[var(--panel)] px-3 py-3 sm:px-4'
      onSubmit={onSubmit}
    >
      <label className='block'>
        <span className='sr-only'>Message to buyer</span>
        <textarea
          className='min-h-20 w-full resize-none rounded-[22px] border border-[var(--line)] bg-white px-4 py-3 text-sm leading-6 text-[var(--ink-strong)] outline-none transition focus:border-[var(--accent)]'
          disabled={disabled || !session}
          onChange={(event) => onChange(event.target.value)}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) {
              event.preventDefault();
              event.currentTarget.form?.requestSubmit();
            }
          }}
          placeholder='Write one offer message. Example: Option 3, but price 5 euro higher.'
          value={draft}
        />
      </label>

      <div className='mt-3 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between'>
        <div className='space-y-3'>
          {error && (
            <div className='rounded-2xl border border-[var(--danger-ink)]/15 bg-[var(--danger-soft)] px-4 py-3 text-sm leading-6 text-[var(--danger-ink)]'>
              {error}
            </div>
          )}

          {parsedDraft.complete ? (
            <div className='flex flex-wrap gap-2 text-sm text-[var(--ink-muted)]'>
              <DetectedTerm
                label='Price'
                value={formatMoney(parsedDraft.terms.price)}
              />
              <DetectedTerm
                label={getSessionTermField("paymentDays")?.label ?? "Payment days"}
                value={`${parsedDraft.terms.paymentDays} days`}
              />
              <DetectedTerm
                label={
                  getSessionTermField("deliveryDays")?.label ?? "Delivery time"
                }
                value={`${parsedDraft.terms.deliveryDays} days`}
              />
              <DetectedTerm
                label={
                  getSessionTermField("contractMonths")?.label ??
                  "Contract length"
                }
                value={`${parsedDraft.terms.contractMonths} months`}
              />
              {parsedDraft.inheritedFields?.length > 0 && (
                <span className='rounded-full border border-dashed border-[var(--line)] px-3 py-1.5 text-[12px]'>
                  Reused: {parsedDraft.inheritedFields.join(", ")}
                </span>
              )}
            </div>
          ) : (
            <p className='text-sm text-[var(--ink-muted)]'>
              Mention at least one commercial term. Missing terms are reused
              from the latest confirmed offer or selected buyer option when
              available.
            </p>
          )}
        </div>

        <Button
          disabled={
            disabled || !session || draft.trim().length === 0 || submittingOffer
          }
          type='submit'
        >
          {submittingOffer ? (
            <LoaderCircle className='h-4 w-4 animate-spin' />
          ) : (
            <SendHorizontal className='h-4 w-4' />
          )}
          Send offer
        </Button>
      </div>

      {bounds && (
        <details className='mt-3 rounded-2xl border border-dashed border-[var(--line)] bg-[var(--page-bg)] px-4 py-3 text-sm text-[var(--ink-muted)]'>
          <summary className='cursor-pointer text-[12px] font-semibold uppercase tracking-[0.18em] text-[var(--ink-soft)]'>
            Format help
          </summary>
          <div className='mt-3 space-y-2 leading-7'>
            <p>Allowed ranges:</p>
            <ul>
              {SESSION_TERM_FIELDS.map((field) => (
                <li key={field.key}>{formatTermRange(field.key, bounds)}</li>
              ))}
            </ul>
          </div>
        </details>
      )}
    </form>
  );
}

function DetectedTerm({ label, value }) {
  return (
    <span className='rounded-full border border-[var(--line)] bg-[var(--page-bg)] px-3 py-1.5 text-[12px] font-medium'>
      {label}: {value}
    </span>
  );
}
