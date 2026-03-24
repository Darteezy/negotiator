import botAvatar from "@/assets/robot.svg";

interface Props {
  title: string;
  actor: "buyer" | "supplier";
  message?: string;
  align?: "left" | "right";
  highlight?: boolean;
  mesoLabel?: string;
  detailRows?: Array<{ label: string; value: string }>;
}

export function OfferCard({
  title,
  actor,
  message,
  align = "left",
  highlight = false,
  mesoLabel,
  detailRows = [],
}: Props) {
  const isBuyer = actor === "buyer";
  const alignment =
    align === "right" ? "items-end text-right" : "items-start text-left";
  const detailsSummaryAlignment =
    align === "right" ? "justify-end text-right" : "justify-start text-left";
  const bubble = isBuyer
    ? "bg-[rgba(33,60,72,0.92)] text-[var(--ink-strong)]"
    : "bg-[rgba(18,39,49,0.92)] text-[var(--ink-strong)]";

  return (
    <div className={`flex flex-col gap-2 ${alignment}`}>
      <div className='flex items-center gap-2 text-xs uppercase tracking-wide text-[var(--ink-muted)]'>
        {isBuyer ? (
          <img
            src={botAvatar}
            alt='Buyer bot'
            className='h-8 w-8 rounded-full border border-[var(--line)] bg-[rgba(18,39,49,0.92)] shadow-sm shadow-black/20'
          />
        ) : (
          <span className='flex h-8 w-8 items-center justify-center rounded-full bg-[rgba(18,39,49,0.92)] text-[11px] font-semibold text-[var(--ink-strong)] shadow-sm shadow-black/10 ring-1 ring-[var(--line)]'>
            S
          </span>
        )}
        <div className='flex flex-col'>
          <span className='font-semibold text-[var(--ink-strong)]'>
            {title}
          </span>
          {mesoLabel ? (
            <span className='text-[11px] font-semibold text-[var(--ink-soft)]'>
              {mesoLabel}
            </span>
          ) : null}
        </div>
      </div>
      <div
        className={`w-full max-w-3xl rounded-2xl border border-[var(--line)] px-4 py-3 shadow-sm shadow-black/10 ${bubble} ${highlight ? "ring-2 ring-[var(--accent)]" : ""}`}
      >
        {message ? (
          <p className='text-[13px] leading-6 whitespace-pre-wrap'>{message}</p>
        ) : null}
        {detailRows.length > 0 ? (
          <details className='mt-3 rounded-xl border border-[var(--line)] bg-black/10 px-3 py-2 text-[12px] text-[var(--ink-soft)]'>
            <summary
              className={`flex cursor-pointer list-none items-center gap-2 font-semibold text-[var(--ink-muted)] ${detailsSummaryAlignment}`}
            >
              <span>Details▼</span>
            </summary>
            <div className='mt-3 grid grid-cols-1 gap-2 md:grid-cols-2'>
              {detailRows.map((row) => (
                <div
                  key={`${row.label}-${row.value}`}
                  className='rounded-xl border border-[var(--line)] bg-[var(--panel)]/80 px-3 py-2 text-left'
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
        ) : null}
      </div>
    </div>
  );
}
