import { Bot, HandCoins, RefreshCcw } from "lucide-react";

import { buildDebugTimeline } from "@/lib/conversation-view";
import { formatDateTime, sentenceCase } from "@/lib/format";
import { Badge } from "@/components/ui/badge";
import { TermsGrid } from "@/components/terms-grid";

export function DebugPane({
  pendingSupplierMessage = "",
  session,
  supplierMessages = [],
}) {
  const timeline = buildDebugTimeline(
    session,
    supplierMessages,
    pendingSupplierMessage,
  );

  return (
    <section className='flex min-h-0 flex-1 flex-col bg-[#efede6]'>
      <div className='flex flex-wrap items-center justify-between gap-2 border-b border-[var(--line)] px-2 py-1.5'>
        <div className='app-mono text-[11px] uppercase tracking-[0.12em] text-[var(--ink-soft)]'>
          debug
        </div>
        <Badge tone='neutral'>{timeline.length} tracked events</Badge>
      </div>

      <div className='flex-1 space-y-1.5 overflow-y-auto px-1.5 py-1.5 sm:px-2'>
        {timeline.map((event, index) => (
          <article
            key={`${event.actor}-${event.at}-${index}`}
            className='border border-[var(--line)] bg-white px-2 py-1.5 shadow-none'
          >
            <div className='mb-1.5 flex flex-wrap items-center justify-between gap-2'>
              <div className='flex items-center gap-2'>
                <div className='flex h-5 w-5 items-center justify-center border bg-white text-[var(--ink-strong)]'>
                  {event.actor === "supplier" ? (
                    <HandCoins className='h-3 w-3 text-[var(--supplier-ink)]' />
                  ) : event.actor === "system" ? (
                    <RefreshCcw className='h-3 w-3 text-[var(--accent)]' />
                  ) : (
                    <Bot className='h-3 w-3 text-[var(--buyer-ink)]' />
                  )}
                </div>
                <div className='flex flex-wrap items-center gap-2'>
                  <p className='text-[12px] font-semibold text-[var(--ink-strong)]'>
                    {event.title}
                  </p>
                  <p className='app-mono text-[10px] text-[var(--ink-muted)]'>
                    {formatDateTime(event.at)}
                  </p>
                  {event.links?.map((link) => (
                    <a
                      key={`${event.at}-${link.messageId}`}
                      className='app-mono text-[10px] text-[var(--accent)] underline underline-offset-2'
                      href={link.href}
                    >
                      {link.messageId}
                    </a>
                  ))}
                </div>
              </div>

              <Badge
                tone={
                  event.actor === "supplier"
                    ? "supplier"
                    : event.actor === "buyer"
                      ? "buyer"
                      : "neutral"
                }
              >
                {sentenceCase(event.actor)}
              </Badge>
            </div>

            <div className='space-y-2'>
              <div className='border border-[var(--line)] bg-[#faf8f2] px-2 py-1.5 text-[12px] leading-5 text-[var(--ink-muted)]'>
                {event.summary}
              </div>

              {event.terms && <TermsGrid terms={event.terms} />}

              {event.counterOffers?.length > 0 && (
                <div className='space-y-1'>
                  <p className='app-mono text-[10px] uppercase tracking-[0.12em] text-[var(--ink-soft)]'>
                    Counteroffers returned
                  </p>
                  {event.counterOffers.map((offer, optionIndex) => (
                    <div
                      key={`${event.at}-${optionIndex}`}
                      className='space-y-1'
                    >
                      {event.counterOffers.length > 1 && (
                        <p className='app-mono text-[10px] uppercase tracking-[0.12em] text-[var(--ink-soft)]'>
                          Option {optionIndex + 1}
                        </p>
                      )}
                      <TermsGrid terms={offer} />
                    </div>
                  ))}
                </div>
              )}

              {event.debug && (
                <div className='border border-dashed border-[var(--line)] bg-[var(--panel)] px-2 py-1.5 text-[12px] text-[var(--ink-muted)]'>
                  <div className='flex flex-wrap gap-2'>
                    {event.debug.strategy && (
                      <Badge tone='buyer'>
                        {sentenceCase(event.debug.strategy)}
                      </Badge>
                    )}
                    {event.debug.switchTrigger && (
                      <Badge tone='neutral'>
                        Trigger: {sentenceCase(event.debug.switchTrigger)}
                      </Badge>
                    )}
                    {event.debug.reasonCode && (
                      <Badge tone='buyer'>
                        {sentenceCase(event.debug.reasonCode)}
                      </Badge>
                    )}
                    {event.debug.focusIssue && (
                      <Badge tone='neutral'>
                        Focus: {sentenceCase(event.debug.focusIssue)}
                      </Badge>
                    )}
                    {event.debug.reasonLabel && (
                      <Badge tone='neutral'>{event.debug.reasonLabel}</Badge>
                    )}
                  </div>

                  {event.debug.narrative && (
                    <p className='mt-2 leading-5'>{event.debug.narrative}</p>
                  )}

                  {event.debug.evaluation && (
                    <div className='mt-2 grid gap-1.5 sm:grid-cols-2 xl:grid-cols-4'>
                      <DebugMetric
                        label='Buyer utility'
                        value={event.debug.evaluation.buyerUtility}
                      />
                      <DebugMetric
                        label='Target utility'
                        value={event.debug.evaluation.targetUtility}
                      />
                      <DebugMetric
                        label='Supplier estimate'
                        value={event.debug.evaluation.estimatedSupplierUtility}
                      />
                      <DebugMetric
                        label='Nash product'
                        value={event.debug.evaluation.nashProduct}
                      />
                    </div>
                  )}

                  {event.debug.counterOfferSummary?.length > 0 && (
                    <div className='mt-2 space-y-1'>
                      <p className='app-mono text-[10px] uppercase tracking-[0.12em] text-[var(--ink-soft)]'>
                        Trade-off explanation
                      </p>
                      <ul className='space-y-0.5'>
                        {event.debug.counterOfferSummary.map((summary) => (
                          <li key={summary}>{summary}</li>
                        ))}
                      </ul>
                    </div>
                  )}
                </div>
              )}
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

function DebugMetric({ label, value }) {
  return (
    <div className='border border-[var(--line)] bg-white px-2 py-1.5'>
      <p className='app-mono text-[10px] uppercase tracking-[0.12em] text-[var(--ink-soft)]'>
        {label}
      </p>
      <p className='mt-1 app-mono text-[12px] font-semibold text-[var(--ink-strong)]'>
        {value ?? "—"}
      </p>
    </div>
  );
}
