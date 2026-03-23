import { LoaderCircle, RotateCcw, SlidersHorizontal } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SESSION_TERM_FIELDS } from "@/lib/session-config";

export function SessionSetupScreen({
  bounds,
  config,
  error,
  loadingDefaults,
  onFieldChange,
  onReset,
  onSessionSettingChange,
  onStartSession,
  startingSession,
}) {
  return (
    <section className='min-h-screen bg-[radial-gradient(circle_at_top_left,_rgba(14,124,102,0.12),_transparent_38%),linear-gradient(180deg,_rgba(255,252,245,0.94),_rgba(244,239,231,1))] px-4 py-6 sm:px-6 lg:px-8'>
      <div className='mx-auto flex min-h-[calc(100vh-3rem)] max-w-6xl flex-col justify-center gap-6'>
        <div className='grid gap-4 lg:grid-cols-[1.35fr_0.9fr]'>
          <div className='border border-[var(--line)] bg-[var(--panel)] p-5 shadow-[0_24px_80px_rgba(29,42,47,0.08)] sm:p-6'>
            <div className='flex flex-wrap items-start justify-between gap-4'>
              <div className='max-w-2xl space-y-3'>
                <div className='app-mono text-[11px] uppercase tracking-[0.16em] text-[var(--accent)]'>
                  Buyer setup
                </div>
                <div className='space-y-2'>
                  <h1 className='text-3xl font-semibold tracking-[-0.04em] text-[var(--ink-strong)] sm:text-4xl'>
                    Configure the buyer mandate before the negotiation opens.
                  </h1>
                  <p className='max-w-2xl text-sm leading-7 text-[var(--ink-muted)] sm:text-[15px]'>
                    Set the buyer target and hard limit for price, payment
                    days, delivery time, and contract length. These values are
                    sent into the negotiation engine and persisted with the
                    session.
                  </p>
                </div>
              </div>

              <div className='flex items-center gap-2 rounded-full border border-[var(--line)] bg-white px-3 py-2 text-sm text-[var(--ink-muted)]'>
                <SlidersHorizontal className='h-4 w-4 text-[var(--accent)]' />
                Goal and limit driven
              </div>
            </div>

            {error ? (
              <div className='mt-5 rounded-2xl border border-[var(--danger-ink)]/15 bg-[var(--danger-soft)] px-4 py-3 text-sm leading-6 text-[var(--danger-ink)]'>
                {error}
              </div>
            ) : null}

            <div className='mt-5 grid gap-4 md:grid-cols-2'>
              {SESSION_TERM_FIELDS.map((field) => (
                <article
                  key={field.key}
                  className='border border-[var(--line)] bg-white/80 p-4'
                >
                  <div className='flex items-start justify-between gap-3'>
                    <div>
                      <h2 className='text-lg font-semibold tracking-[-0.02em] text-[var(--ink-strong)]'>
                        {field.label}
                      </h2>
                      <p className='mt-1 text-sm leading-6 text-[var(--ink-muted)]'>
                        {field.goalDescription}
                      </p>
                    </div>
                    {bounds ? (
                      <div className='app-mono text-[10px] uppercase tracking-[0.12em] text-[var(--ink-soft)]'>
                        {bounds[field.minKey]} to {bounds[field.maxKey]}{" "}
                        {field.unit}
                      </div>
                    ) : null}
                  </div>

                  <div className='mt-4 grid gap-3 sm:grid-cols-2'>
                    <label className='space-y-2'>
                      <span className='text-[12px] font-semibold uppercase tracking-[0.12em] text-[var(--ink-soft)]'>
                        Goal
                      </span>
                      <Input
                        inputMode='decimal'
                        min={bounds?.[field.minKey]}
                        max={bounds?.[field.maxKey]}
                        onChange={(event) =>
                          onFieldChange("idealOffer", field.key, event.target.value)
                        }
                        step={field.step}
                        type='number'
                        value={config?.idealOffer?.[field.key] ?? ""}
                      />
                      <p className='text-xs leading-5 text-[var(--ink-muted)]'>
                        {field.goalDescription}
                      </p>
                    </label>

                    <label className='space-y-2'>
                      <span className='text-[12px] font-semibold uppercase tracking-[0.12em] text-[var(--ink-soft)]'>
                        Limit
                      </span>
                      <Input
                        inputMode='decimal'
                        min={bounds?.[field.minKey]}
                        max={bounds?.[field.maxKey]}
                        onChange={(event) =>
                          onFieldChange(
                            "reservationOffer",
                            field.key,
                            event.target.value,
                          )
                        }
                        step={field.step}
                        type='number'
                        value={config?.reservationOffer?.[field.key] ?? ""}
                      />
                      <p className='text-xs leading-5 text-[var(--ink-muted)]'>
                        {field.limitDescription}
                      </p>
                    </label>
                  </div>
                </article>
              ))}
            </div>
          </div>

          <aside className='border border-[var(--line)] bg-[#fbf8f1] p-5 shadow-[0_24px_80px_rgba(29,42,47,0.05)] sm:p-6'>
            <div className='space-y-5'>
              <div>
                <div className='app-mono text-[11px] uppercase tracking-[0.16em] text-[var(--ink-soft)]'>
                  Buyer policy
                </div>
                <h2 className='mt-2 text-2xl font-semibold tracking-[-0.03em] text-[var(--ink-strong)]'>
                  One fixed buyer policy.
                </h2>
              </div>

              <label className='block space-y-2'>
                <span className='text-[12px] font-semibold uppercase tracking-[0.12em] text-[var(--ink-soft)]'>
                  Max rounds
                </span>
                <Input
                  min='1'
                  onChange={(event) =>
                    onSessionSettingChange("maxRounds", event.target.value)
                  }
                  step='1'
                  type='number'
                  value={config?.maxRounds ?? ""}
                />
              </label>

              <div className='border border-dashed border-[var(--line)] bg-white/70 p-4 text-sm leading-6 text-[var(--ink-muted)]'>
                The buyer follows one baseline policy across every session. It
                accepts strong offers early, counters with one revised offer,
                and only gives limited price relief when the supplier improves a
                non-price term.
              </div>

              <div className='flex flex-col gap-3'>
                <Button
                  className='w-full'
                  disabled={loadingDefaults || startingSession || !config}
                  onClick={onStartSession}
                  type='button'
                >
                  {loadingDefaults || startingSession ? (
                    <LoaderCircle className='h-4 w-4 animate-spin' />
                  ) : null}
                  Start negotiation
                </Button>

                <Button
                  className='w-full'
                  disabled={loadingDefaults || !config}
                  onClick={onReset}
                  type='button'
                  variant='ghost'
                >
                  <RotateCcw className='h-4 w-4' />
                  Reset buyer defaults
                </Button>
              </div>

              <p className='text-xs leading-5 text-[var(--ink-muted)]'>
                The live negotiation uses this exact buyer mandate inside the
                fixed calibrated domain for the challenge.
              </p>
            </div>
          </aside>
        </div>
      </div>
    </section>
  );
}
