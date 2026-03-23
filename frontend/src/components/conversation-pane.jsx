import { createElement } from "react";

import { Bot, HandCoins, RefreshCcw } from "lucide-react";

import { buildChatTranscript } from "@/lib/conversation-view";
import { formatDateTime, sentenceCase } from "@/lib/format";
import { Badge } from "@/components/ui/badge";

export function ConversationPane({
  loading,
  pendingSupplierMessage = "",
  session,
  supplierMessages = [],
}) {
  const transcript = buildChatTranscript(
    session,
    supplierMessages,
    pendingSupplierMessage,
  );

  return (
    <section className='flex min-h-0 flex-1 flex-col bg-[#f6f2e8]'>
      <div className='flex flex-wrap items-center justify-between gap-2 border-b border-[var(--line)] px-2 py-1.5'>
        <div className='app-mono text-[11px] uppercase tracking-[0.12em] text-[var(--ink-soft)]'>
          chat
        </div>
        {session && (
          <>
            <Badge tone='neutral'>
              Round {session.currentRound} / {session.maxRounds}
            </Badge>
            <Badge tone={statusTone(session.status)}>
              {sentenceCase(session.status)}
            </Badge>
            <Badge tone='buyer'>{sentenceCase(session.strategy)}</Badge>
          </>
        )}
      </div>

      <div className='flex-1 space-y-1.5 overflow-y-auto px-1.5 py-1.5 sm:px-2'>
        {loading && (
          <TimelinePlaceholder
            title='Connecting to the negotiation backend'
            copy='Opening the supplier chat and loading the first buyer strategy.'
          />
        )}

        {!loading && transcript.length === 0 && (
          <TimelinePlaceholder
            title='Connected to buyer'
            copy='Send the first offer message to begin the negotiation.'
          />
        )}

        {transcript.map((event, index) => (
          <MessageCard
            key={`${event.eventType}-${event.at}-${index}`}
            actor={actorTone(event.actor)}
            anchorId={event.anchorId}
            icon={actorIcon(event.actor)}
            messageId={event.messageId}
            title={actorTitle(event.actor, event.title)}
            timestamp={event.at}
          >
            <div className='whitespace-pre-line border border-[var(--line)] bg-white px-2 py-1.5 text-[13px] leading-5 text-[var(--ink-muted)]'>
              {event.message}
            </div>
          </MessageCard>
        ))}
      </div>
    </section>
  );
}

function actorTone(actor) {
  if (actor === "supplier") {
    return "supplier";
  }

  if (actor === "system") {
    return "system";
  }

  return "buyer";
}

function actorTitle(actor, title) {
  if (actor === "supplier") {
    return "You";
  }

  if (actor === "system") {
    return title ?? "System";
  }

  return "Buyer";
}

function actorIcon(actor) {
  if (actor === "supplier") {
    return HandCoins;
  }

  if (actor === "system") {
    return RefreshCcw;
  }

  return Bot;
}

function statusTone(status) {
  if (status === "ACCEPTED") {
    return "success";
  }

  if (status === "REJECTED") {
    return "danger";
  }

  return "neutral";
}

function MessageCard({
  actor,
  anchorId,
  children,
  icon: Icon,
  messageId,
  timestamp,
  title,
}) {
  const containerStyles =
    actor === "supplier"
      ? "ml-auto max-w-3xl"
      : actor === "system"
        ? "mx-auto max-w-2xl"
        : "mr-auto max-w-3xl";

  const actorStyles =
    actor === "supplier"
      ? "border-[var(--supplier-soft)] bg-[var(--supplier-soft)]/72"
      : actor === "system"
        ? "border-[var(--line)] bg-[var(--page-bg)]"
        : "border-[var(--buyer-soft)] bg-[var(--buyer-soft)]/72";

  const iconStyles =
    actor === "supplier"
      ? "bg-white text-[var(--supplier-ink)]"
      : actor === "system"
        ? "bg-white text-[var(--accent)]"
        : "bg-white text-[var(--buyer-ink)]";

  return (
    <article
      id={anchorId}
      className={`animate-in slide-in-from-bottom-2 duration-300 ${containerStyles}`}
    >
      <div className={`border px-2 py-1.5 shadow-none ${actorStyles}`}>
        <div className='mb-1 flex flex-wrap items-center justify-between gap-2'>
          <div className='flex items-center gap-2'>
            <div
              className={`flex h-5 w-5 items-center justify-center border ${iconStyles}`}
            >
              {createElement(Icon, { className: "h-3 w-3" })}
            </div>
            <div className='flex items-center gap-2'>
              <a
                className='app-mono text-[11px] font-semibold text-[var(--accent)] underline underline-offset-2'
                href={`#${anchorId}`}
              >
                {messageId}
              </a>
              <p className='text-[12px] font-semibold text-[var(--ink-strong)]'>
                {title}
              </p>
              <p className='app-mono text-[10px] text-[var(--ink-muted)]'>
                {formatDateTime(timestamp)}
              </p>
            </div>
          </div>
        </div>
        {children}
      </div>
    </article>
  );
}

function TimelinePlaceholder({ copy, title }) {
  return (
    <div className='border border-dashed border-[var(--line)] bg-white px-4 py-5 text-center'>
      <p className='text-sm font-semibold text-[var(--ink-strong)]'>{title}</p>
      <p className='mx-auto mt-2 max-w-xl text-sm leading-6 text-[var(--ink-muted)]'>
        {copy}
      </p>
    </div>
  );
}
