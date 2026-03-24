import type { ApiConversationEvent } from "@/lib/types";

interface Props {
  events: ApiConversationEvent[];
}

export function HistoryLog({ events }: Props) {
  return (
    <div className='rounded-2xl border border-[var(--line)] bg-white/80 p-4 shadow-sm shadow-black/5'>
      <div className='mb-3 flex items-center justify-between'>
        <div>
          <p className='text-xs font-semibold uppercase tracking-wide text-[var(--ink-muted)]'>
            Timeline
          </p>
          <p className='text-sm text-[var(--ink-soft)]'>
            Each round with decisions and utilities.
          </p>
        </div>
      </div>
      <div className='flex flex-col gap-3'>
        {events.map((event) => (
          <div
            key={`${event.eventType}-${event.at}-${event.title}`}
            className='rounded-xl border border-[var(--line)] bg-[var(--panel)] px-3 py-2 shadow-inner shadow-black/5'
          >
            <div className='flex items-center justify-between text-xs font-semibold text-[var(--ink-muted)]'>
              <span>{event.title}</span>
              <span>
                {new Date(event.at).toLocaleTimeString([], {
                  hour: "2-digit",
                  minute: "2-digit",
                })}
              </span>
            </div>
            <p className='mt-1 text-sm text-[var(--ink-strong)] whitespace-pre-wrap'>
              {event.message}
            </p>
            {event.debug?.evaluation?.buyerUtility !== undefined ? (
              <p className='mt-1 text-xs font-semibold text-[var(--accent)]'>
                Utility: {Math.round(event.debug.evaluation.buyerUtility * 100)}
                %
              </p>
            ) : null}
          </div>
        ))}
      </div>
    </div>
  );
}
