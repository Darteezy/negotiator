import { AlertTriangle, Clock3, LoaderCircle, RotateCcw } from "lucide-react";

import { formatMoney, sentenceCase } from "@/lib/format";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

export function StatusPanel({
  defaults,
  error,
  latestReply,
  loadingDefaults,
  onRestartSession,
  session,
  startingSession,
}) {
  return (
    <Card className='border-[var(--line)] bg-[var(--panel)]/96 backdrop-blur'>
      <CardHeader>
        <div className='flex items-center justify-between gap-3'>
          <div>
            <CardTitle className='text-xl tracking-[-0.03em]'>
              Session summary
            </CardTitle>
            <CardDescription className='mt-1 text-sm leading-6 text-[var(--ink-muted)]'>
              Diagnostics are visible here for evaluation, but the main supplier
              experience stays negotiation-first.
            </CardDescription>
          </div>
          {session && (
            <Badge tone={statusTone(session.status)}>
              {sentenceCase(session.status)}
            </Badge>
          )}
        </div>
      </CardHeader>

      <CardContent className='space-y-4'>
        <div className='grid gap-3 sm:grid-cols-2'>
          <MetricCard
            label='Round'
            value={
              session
                ? `${session.currentRound} / ${session.maxRounds}`
                : loadingDefaults
                  ? "…"
                  : `1 / ${defaults?.maxRounds ?? "—"}`
            }
          />
          <MetricCard
            label='Walkaway risk'
            value={
              defaults
                ? `${Math.round(Number(defaults.riskOfWalkaway) * 100)}%`
                : "—"
            }
          />
        </div>

        {loadingDefaults && (
          <div className='flex items-center gap-3 rounded-2xl border border-[var(--line)] bg-[var(--page-bg)] px-4 py-4 text-sm text-[var(--ink-muted)]'>
            <LoaderCircle className='h-4 w-4 animate-spin text-[var(--accent)]' />
            Fetching backend defaults.
          </div>
        )}

        {error && (
          <div className='flex items-start gap-3 rounded-2xl border border-[var(--danger-ink)]/15 bg-[var(--danger-soft)] px-4 py-4 text-sm leading-6 text-[var(--danger-ink)]'>
            <AlertTriangle className='mt-0.5 h-4 w-4 shrink-0' />
            <span>{error}</span>
          </div>
        )}

        {latestReply && (
          <div className='space-y-3 rounded-3xl border border-[var(--line)] bg-[var(--page-bg)] p-4'>
            <div className='flex items-center justify-between gap-3'>
              <p className='text-[13px] font-semibold uppercase tracking-[0.2em] text-[var(--ink-soft)]'>
                Latest buyer reply
              </p>
              <Badge tone={statusTone(latestReply.resultingStatus)}>
                {sentenceCase(latestReply.resultingStatus)}
              </Badge>
            </div>
            <p className='text-sm leading-7 text-[var(--ink-muted)]'>
              {latestReply.explanation}
            </p>

            <div className='grid gap-3 sm:grid-cols-2'>
              <MetricCard
                label='Buyer utility'
                value={latestReply.evaluation?.buyerUtility ?? "—"}
                compact
              />
              <MetricCard
                label='Target utility'
                value={latestReply.evaluation?.targetUtility ?? "—"}
                compact
              />
              <MetricCard
                label='Supplier estimate'
                value={latestReply.evaluation?.estimatedSupplierUtility ?? "—"}
                compact
              />
              <MetricCard
                label='Nash product'
                value={latestReply.evaluation?.nashProduct ?? "—"}
                compact
              />
            </div>
          </div>
        )}

        <div className='rounded-3xl border border-[var(--line)] bg-[var(--page-bg)] p-4'>
          <div className='flex items-center gap-3'>
            <div className='flex h-10 w-10 items-center justify-center rounded-2xl bg-[var(--buyer-soft)] text-[var(--buyer-ink)]'>
              <Clock3 className='h-5 w-5' />
            </div>
            <div>
              <p className='text-sm font-semibold text-[var(--ink-strong)]'>
                Current buyer messaging layer
              </p>
              <p className='text-sm leading-6 text-[var(--ink-muted)]'>
                Responses are currently sourced from the backend explanation
                text. AI-generated negotiation phrasing can be added later on
                top of the same decision contract.
              </p>
            </div>
          </div>
        </div>

        {session && (
          <Button
            className='w-full'
            disabled={startingSession}
            onClick={onRestartSession}
            type='button'
            variant='secondary'
          >
            <RotateCcw className='h-4 w-4' />
            Start a fresh session
          </Button>
        )}
      </CardContent>
    </Card>
  );
}

function MetricCard({ compact = false, label, value }) {
  return (
    <div
      className={`rounded-2xl border border-[var(--line)] bg-[var(--page-bg)] ${compact ? "px-3 py-3" : "px-4 py-4"}`}
    >
      <p className='text-[11px] uppercase tracking-[0.18em] text-[var(--ink-soft)]'>
        {label}
      </p>
      <p
        className={`mt-2 ${compact ? "app-mono text-sm" : "text-lg"} font-semibold text-[var(--ink-strong)]`}
      >
        {typeof value === "number" ? formatMoney(value) : value}
      </p>
    </div>
  );
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
