import { createElement } from "react";

import { Bot, HandCoins } from "lucide-react";

import { sentenceCase, formatDateTime } from "@/lib/format";
import { Badge } from "@/components/ui/badge";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { TermsGrid } from "@/components/terms-grid";

export function ConversationPane({ loading, session }) {
  return (
    <Card className='border-[var(--line)] bg-[var(--panel)]/96 backdrop-blur'>
      <CardHeader className='border-b border-[var(--line)] pb-5'>
        <div className='flex items-center justify-between gap-3'>
          <div>
            <CardTitle className='text-2xl tracking-[-0.04em]'>
              Negotiation timeline
            </CardTitle>
            <CardDescription className='mt-2 max-w-2xl text-sm leading-6 text-[var(--ink-muted)]'>
              Supplier offers and buyer decisions are rendered from persisted
              negotiation rounds. This transcript is the live system state, not
              a demo conversation.
            </CardDescription>
          </div>
          {session && (
            <Badge tone='neutral'>
              {session.rounds.length} recorded rounds
            </Badge>
          )}
        </div>
      </CardHeader>

      <CardContent className='space-y-4 pt-6'>
        {loading && (
          <TimelinePlaceholder
            title='Connecting to the negotiation backend'
            copy='Loading bounds and session defaults so the supplier console can open against live endpoints.'
          />
        )}

        {!loading && !session && (
          <TimelinePlaceholder
            title='No active session yet'
            copy='Start a session from the composer panel to open a real negotiation round with the buyer agent.'
          />
        )}

        {session?.rounds.map((round) => (
          <div key={round.roundNumber} className='space-y-3'>
            <MessageCard
              actor='supplier'
              icon={HandCoins}
              title={`Supplier offer • Round ${round.roundNumber}`}
              timestamp={round.supplierOffer.at}
            >
              <TermsGrid terms={round.supplierOffer.terms} />
            </MessageCard>

            <MessageCard
              actor='buyer'
              icon={Bot}
              title={`Buyer ${sentenceCase(round.buyerReply.decision)}`}
              timestamp={round.buyerReply.decidedAt}
            >
              <div className='space-y-4'>
                <div className='rounded-2xl border border-[var(--line)] bg-white/70 px-4 py-4 text-sm leading-7 text-[var(--ink-muted)]'>
                  {round.buyerReply.explanation}
                </div>
                {round.buyerReply.counterOffer && (
                  <div className='space-y-2'>
                    <div className='flex items-center justify-between gap-3'>
                      <p className='text-xs font-semibold uppercase tracking-[0.2em] text-[var(--ink-soft)]'>
                        Buyer counteroffer
                      </p>
                      <Badge tone={badgeTone(round.buyerReply.resultingStatus)}>
                        {sentenceCase(round.buyerReply.resultingStatus)}
                      </Badge>
                    </div>
                    <TermsGrid terms={round.buyerReply.counterOffer} />
                  </div>
                )}
                {!round.buyerReply.counterOffer && (
                  <div className='flex items-center justify-between gap-3 rounded-2xl border border-dashed border-[var(--line)] px-4 py-3 text-sm text-[var(--ink-muted)]'>
                    <span>No counteroffer returned in this round.</span>
                    <Badge tone={badgeTone(round.buyerReply.resultingStatus)}>
                      {sentenceCase(round.buyerReply.resultingStatus)}
                    </Badge>
                  </div>
                )}
              </div>
            </MessageCard>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function MessageCard({ actor, children, icon: Icon, timestamp, title }) {
  const actorStyles =
    actor === "supplier"
      ? "border-[var(--supplier-soft)] bg-[var(--supplier-soft)]/70"
      : "border-[var(--buyer-soft)] bg-[var(--buyer-soft)]/72";

  const iconStyles =
    actor === "supplier"
      ? "bg-white text-[var(--supplier-ink)]"
      : "bg-white text-[var(--buyer-ink)]";

  return (
    <article
      className={`animate-in slide-in-from-bottom-2 duration-300 rounded-[26px] border p-4 shadow-sm sm:p-5 ${actorStyles}`}
    >
      <div className='mb-4 flex flex-wrap items-start justify-between gap-3'>
        <div className='flex items-center gap-3'>
          <div
            className={`flex h-11 w-11 items-center justify-center rounded-2xl ${iconStyles}`}
          >
            {createElement(Icon, { className: "h-5 w-5" })}
          </div>
          <div>
            <p className='text-base font-semibold text-[var(--ink-strong)]'>
              {title}
            </p>
            <p className='text-sm text-[var(--ink-muted)]'>
              {formatDateTime(timestamp)}
            </p>
          </div>
        </div>
      </div>
      {children}
    </article>
  );
}

function TimelinePlaceholder({ copy, title }) {
  return (
    <div className='rounded-[26px] border border-dashed border-[var(--line)] bg-[var(--page-bg)] px-5 py-10 text-center'>
      <p className='text-lg font-semibold text-[var(--ink-strong)]'>{title}</p>
      <p className='mx-auto mt-2 max-w-xl text-sm leading-7 text-[var(--ink-muted)]'>
        {copy}
      </p>
    </div>
  );
}

function badgeTone(status) {
  if (status === "ACCEPTED") {
    return "success";
  }

  if (status === "REJECTED") {
    return "danger";
  }

  return "buyer";
}
