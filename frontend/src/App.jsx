import { createElement, useEffect, useMemo, useState } from "react";
import {
  LoaderCircle,
  MessageSquareQuote,
  RefreshCcw,
  Rocket,
  ShieldCheck,
} from "lucide-react";

import {
  fetchSessionDefaults,
  startSession,
  submitSupplierOffer,
} from "@/api/negotiation";
import { ConversationPane } from "@/components/conversation-pane";
import { OfferComposer } from "@/components/offer-composer";
import { StatusPanel } from "@/components/status-panel";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";

function App() {
  const [defaults, setDefaults] = useState(null);
  const [session, setSession] = useState(null);
  const [offerDraft, setOfferDraft] = useState(null);
  const [loadingDefaults, setLoadingDefaults] = useState(true);
  const [startingSession, setStartingSession] = useState(false);
  const [submittingOffer, setSubmittingOffer] = useState(false);
  const [error, setError] = useState("");

  const bounds = session?.bounds ?? defaults?.bounds ?? null;
  const latestReply = session?.rounds?.at(-1)?.buyerReply ?? null;

  useEffect(() => {
    let cancelled = false;

    async function loadDefaults() {
      try {
        setLoadingDefaults(true);
        setError("");
        const nextDefaults = await fetchSessionDefaults();

        if (cancelled) {
          return;
        }

        setDefaults(nextDefaults);
        setOfferDraft(
          (currentDraft) =>
            currentDraft ?? buildSuggestedOffer(nextDefaults.bounds),
        );
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError.message);
        }
      } finally {
        if (!cancelled) {
          setLoadingDefaults(false);
        }
      }
    }

    loadDefaults();

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!bounds) {
      return;
    }

    setOfferDraft(
      (currentDraft) => currentDraft ?? buildSuggestedOffer(bounds),
    );
  }, [bounds]);

  const sessionStage = useMemo(() => {
    if (loadingDefaults) {
      return "Connecting to backend";
    }

    if (!session) {
      return "Ready to open a live negotiation session";
    }

    if (session.closed) {
      return "Session closed";
    }

    return "Negotiation in progress";
  }, [loadingDefaults, session]);

  async function handleStartSession() {
    try {
      setStartingSession(true);
      setError("");
      const nextSession = await startSession();
      setSession(nextSession);
      setOfferDraft(buildSuggestedOffer(nextSession.bounds));
    } catch (startError) {
      setError(startError.message);
    } finally {
      setStartingSession(false);
    }
  }

  async function handleRestartSession() {
    setSession(null);
    await handleStartSession();
  }

  async function handleSubmitOffer(event) {
    event.preventDefault();

    if (!session?.id || !offerDraft) {
      return;
    }

    try {
      setSubmittingOffer(true);
      setError("");
      const nextSession = await submitSupplierOffer(
        session.id,
        normalizeOfferDraft(offerDraft),
      );
      setSession(nextSession);
    } catch (submitError) {
      setError(submitError.message);
    } finally {
      setSubmittingOffer(false);
    }
  }

  return (
    <main className='relative min-h-screen overflow-hidden bg-[var(--page-bg)] text-[var(--ink-strong)]'>
      <div className='absolute inset-0 bg-[radial-gradient(circle_at_top_left,_rgba(14,124,102,0.14),_transparent_36%),radial-gradient(circle_at_bottom_right,_rgba(196,121,37,0.12),_transparent_28%)]' />
      <div className='absolute inset-0 opacity-40 [background-image:linear-gradient(rgba(29,42,47,0.05)_1px,transparent_1px),linear-gradient(90deg,rgba(29,42,47,0.05)_1px,transparent_1px)] [background-size:42px_42px]' />

      <div className='relative mx-auto flex min-h-screen w-full max-w-7xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8'>
        <header className='grid gap-4 lg:grid-cols-[minmax(0,1fr)_360px]'>
          <Card className='border-[var(--line)] bg-[var(--panel)]/92 backdrop-blur'>
            <CardHeader className='gap-4'>
              <div className='flex flex-wrap items-center justify-between gap-3'>
                <div className='space-y-2'>
                  <Badge className='bg-[var(--buyer-soft)] text-[var(--buyer-ink)]'>
                    Buyer-side agent
                  </Badge>
                  <CardTitle className='text-3xl font-semibold tracking-[-0.04em] sm:text-4xl'>
                    Supplier negotiation console
                  </CardTitle>
                  <CardDescription className='max-w-2xl text-sm leading-6 text-[var(--ink-muted)] sm:text-base'>
                    This interface is designed for the human supplier. Offers
                    are submitted as structured commercial terms, while the
                    buyer responds with live decisions and explanations from the
                    real backend engine.
                  </CardDescription>
                </div>

                <div className='flex items-center gap-2 rounded-full border border-[var(--line)] bg-[var(--page-bg)] px-4 py-2 shadow-sm'>
                  <span className='h-2.5 w-2.5 rounded-full bg-[var(--accent)] shadow-[0_0_0_4px_rgba(14,124,102,0.15)]' />
                  <div>
                    <p className='text-xs uppercase tracking-[0.24em] text-[var(--ink-soft)]'>
                      Session state
                    </p>
                    <p className='text-sm font-medium text-[var(--ink-strong)]'>
                      {sessionStage}
                    </p>
                  </div>
                </div>
              </div>

              <div className='grid gap-3 sm:grid-cols-3'>
                <HeroMetric
                  icon={ShieldCheck}
                  label='Interaction model'
                  value='Structured supplier offers'
                  copy='Keeps the current rule-based buyer engine testable and honest.'
                />
                <HeroMetric
                  icon={MessageSquareQuote}
                  label='Buyer response'
                  value='Human-readable explanations'
                  copy='Current replies come from deterministic engine explanations. AI phrasing can layer on later.'
                />
                <HeroMetric
                  icon={Rocket}
                  label='Future-ready'
                  value='Prepared for AI chat'
                  copy='A conversational wrapper can be added later without replacing the underlying negotiation logic.'
                />
              </div>
            </CardHeader>
          </Card>

          <StatusPanel
            defaults={defaults}
            error={error}
            latestReply={latestReply}
            loadingDefaults={loadingDefaults}
            onRestartSession={handleRestartSession}
            session={session}
            startingSession={startingSession}
          />
        </header>

        <section className='grid flex-1 gap-6 lg:grid-cols-[minmax(0,1fr)_360px]'>
          <ConversationPane loading={loadingDefaults} session={session} />

          <div className='space-y-6'>
            <OfferComposer
              bounds={bounds}
              disabled={!session || session.closed || submittingOffer}
              draft={offerDraft}
              onChange={setOfferDraft}
              onStartSession={handleStartSession}
              onSubmit={handleSubmitOffer}
              session={session}
              startingSession={startingSession}
              submittingOffer={submittingOffer}
            />

            <Card className='border-[var(--line)] bg-[var(--panel)]/96 backdrop-blur'>
              <CardHeader>
                <CardTitle className='text-lg font-semibold tracking-[-0.03em]'>
                  Connection model
                </CardTitle>
                <CardDescription className='text-sm leading-6 text-[var(--ink-muted)]'>
                  The frontend talks directly to the Spring Boot backend over
                  the live negotiation API. No mock adapter is used in this
                  version.
                </CardDescription>
              </CardHeader>
              <CardContent className='space-y-3 text-sm text-[var(--ink-muted)]'>
                <ConnectionRow
                  label='Create session'
                  value='POST /api/negotiations/sessions'
                />
                <ConnectionRow
                  label='Load defaults'
                  value='GET /api/negotiations/config/defaults'
                />
                <ConnectionRow
                  label='Submit offer'
                  value='POST /api/negotiations/sessions/{id}/offers'
                />
                <div className='rounded-2xl border border-dashed border-[var(--line)] bg-[var(--page-bg)] px-4 py-3 text-[13px] leading-6 text-[var(--ink-soft)]'>
                  A future AI message layer should sit above these endpoints,
                  not replace them. The source of truth remains the structured
                  term submission and the backend decision loop.
                </div>
              </CardContent>
            </Card>
          </div>
        </section>
      </div>
    </main>
  );
}

function HeroMetric({ icon: Icon, label, value, copy }) {
  return (
    <div className='rounded-3xl border border-[var(--line)] bg-[var(--page-bg)]/80 p-4 shadow-[0_12px_32px_rgba(29,42,47,0.07)]'>
      <div className='mb-3 flex h-10 w-10 items-center justify-center rounded-2xl bg-[var(--buyer-soft)] text-[var(--buyer-ink)]'>
        {createElement(Icon, { className: "h-5 w-5" })}
      </div>
      <p className='text-xs uppercase tracking-[0.24em] text-[var(--ink-soft)]'>
        {label}
      </p>
      <p className='mt-2 text-base font-semibold text-[var(--ink-strong)]'>
        {value}
      </p>
      <p className='mt-1 text-sm leading-6 text-[var(--ink-muted)]'>{copy}</p>
    </div>
  );
}

function ConnectionRow({ label, value }) {
  return (
    <div className='flex items-center justify-between gap-4 rounded-2xl border border-[var(--line)] bg-[var(--page-bg)] px-4 py-3'>
      <span className='text-[13px] font-medium uppercase tracking-[0.18em] text-[var(--ink-soft)]'>
        {label}
      </span>
      <code className='app-mono rounded-full bg-[var(--buyer-soft)] px-3 py-1 text-xs text-[var(--buyer-ink)]'>
        {value}
      </code>
    </div>
  );
}

function buildSuggestedOffer(bounds) {
  if (!bounds) {
    return null;
  }

  const priceRange = Number(bounds.maxPrice) - Number(bounds.minPrice);
  const paymentRange = bounds.maxPaymentDays - bounds.minPaymentDays;
  const deliveryRange = bounds.maxDeliveryDays - bounds.minDeliveryDays;
  const contractRange = bounds.maxContractMonths - bounds.minContractMonths;

  return {
    price: String((Number(bounds.minPrice) + priceRange * 0.82).toFixed(2)),
    paymentDays: String(
      Math.round(bounds.minPaymentDays + paymentRange * 0.18),
    ),
    deliveryDays: String(
      Math.round(bounds.minDeliveryDays + deliveryRange * 0.73),
    ),
    contractMonths: String(
      Math.round(bounds.minContractMonths + contractRange * 0.72),
    ),
  };
}

function normalizeOfferDraft(draft) {
  return {
    price: Number(draft.price),
    paymentDays: Number(draft.paymentDays),
    deliveryDays: Number(draft.deliveryDays),
    contractMonths: Number(draft.contractMonths),
  };
}

export default App;
