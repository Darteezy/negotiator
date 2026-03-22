import { useEffect, useState } from "react";
import { LoaderCircle } from "lucide-react";

import {
  fetchSessionDefaults,
  parseSupplierOfferWithAi,
  startSession,
  submitSupplierOffer,
} from "@/api/negotiation";
import { ConversationPane } from "@/components/conversation-pane";
import { DebugPane } from "@/components/debug-pane";
import { OfferComposer } from "@/components/offer-composer";
import { Button } from "@/components/ui/button";
import { parseSupplierMessage, shouldUseAiParsing } from "@/lib/chat-offer";

function App() {
  const [defaults, setDefaults] = useState(null);
  const [session, setSession] = useState(null);
  const [sessionOptions, setSessionOptions] = useState(null);
  const [messageDraft, setMessageDraft] = useState("");
  const [loadingDefaults, setLoadingDefaults] = useState(true);
  const [startingSession, setStartingSession] = useState(false);
  const [submittingOffer, setSubmittingOffer] = useState(false);
  const [submittedSupplierMessages, setSubmittedSupplierMessages] = useState(
    [],
  );
  const [pendingSupplierMessage, setPendingSupplierMessage] = useState("");
  const [error, setError] = useState("");

  const bounds = session?.bounds ?? defaults?.bounds ?? null;
  const referenceTerms = resolveReferenceTerms(session);
  const referenceOptions = resolveReferenceOptions(session);
  const parsedDraft = parseSupplierMessage(
    messageDraft,
    bounds,
    referenceTerms,
    referenceOptions,
  );

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
        setSessionOptions({
          strategy: nextDefaults.defaultStrategy,
          maxRounds: String(nextDefaults.maxRounds),
        });
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

  async function handleStartSession() {
    try {
      setStartingSession(true);
      setError("");
      const nextSession = await startSession({
        strategy: sessionOptions?.strategy ?? defaults?.defaultStrategy,
        maxRounds: Number(sessionOptions?.maxRounds ?? defaults?.maxRounds),
      });
      setSession(nextSession);
      setSubmittedSupplierMessages([]);
      setMessageDraft("");
      setPendingSupplierMessage("");
    } catch (startError) {
      setError(startError.message);
    } finally {
      setStartingSession(false);
    }
  }

  async function handleRestartSession() {
    setSubmittedSupplierMessages([]);
    setMessageDraft("");
    setPendingSupplierMessage("");
    setSession(null);
    await handleStartSession();
  }

  async function handleSubmitOffer(event) {
    event.preventDefault();

    if (!session?.id) {
      return;
    }

    try {
      setSubmittingOffer(true);
      setError("");
      setPendingSupplierMessage(messageDraft.trim());
      const resolvedDraft = await resolveDraftTerms({
        bounds,
        counterOffers: referenceOptions,
        messageDraft,
        parsedDraft,
        referenceTerms,
      });

      if (!resolvedDraft.complete) {
        setError(
          `Include ${resolvedDraft.missingFields.join(", ")} in the supplier message.`,
        );
        setPendingSupplierMessage("");
        return;
      }

      if (resolvedDraft.outOfBounds.length > 0) {
        setError(
          `Detected values are outside the allowed range for ${resolvedDraft.outOfBounds.join(", ")}.`,
        );
        setPendingSupplierMessage("");
        return;
      }

      const nextSession = await submitSupplierOffer(session.id, {
        ...resolvedDraft.terms,
        supplierConstraints: resolvedDraft.constraints,
      });
      setSubmittedSupplierMessages((currentMessages) => [
        ...currentMessages,
        messageDraft.trim(),
      ]);
      setSession(nextSession);
      setMessageDraft("");
      setPendingSupplierMessage("");
    } catch (submitError) {
      setError(submitError.message);
      setPendingSupplierMessage("");
    } finally {
      setSubmittingOffer(false);
    }
  }

  return (
    <main className='h-screen overflow-hidden bg-[var(--page-bg)] text-[var(--ink-strong)]'>
      <div className='flex h-screen w-full flex-col overflow-hidden'>
        {!session ? (
          <ConnectScreen
            error={error}
            loadingDefaults={loadingDefaults}
            onStartSession={handleStartSession}
            startingSession={startingSession}
          />
        ) : (
          <div className='flex min-h-0 flex-1 flex-col overflow-hidden'>
            <div className='grid min-h-0 flex-1 grid-cols-[minmax(0,1.35fr)_minmax(20rem,0.85fr)] divide-x divide-[var(--line)]'>
              <ConversationPane
                loading={loadingDefaults}
                pendingSupplierMessage={pendingSupplierMessage}
                session={session}
                supplierMessages={submittedSupplierMessages}
              />
              <DebugPane
                pendingSupplierMessage={pendingSupplierMessage}
                session={session}
                supplierMessages={submittedSupplierMessages}
              />
            </div>
            <OfferComposer
              bounds={bounds}
              disabled={session.closed || submittingOffer}
              draft={messageDraft}
              error={error}
              onChange={setMessageDraft}
              onSubmit={handleSubmitOffer}
              parsedDraft={parsedDraft}
              session={session}
              submittingOffer={submittingOffer}
            />
          </div>
        )}
      </div>
    </main>
  );
}

function resolveReferenceTerms(session) {
  const rounds = session?.rounds ?? [];

  for (let index = rounds.length - 1; index >= 0; index -= 1) {
    const round = rounds[index];
    const buyerTerms =
      round?.buyerReply?.counterOffer ?? round?.buyerReply?.terms;

    if (buyerTerms) {
      return buyerTerms;
    }

    if (round?.supplierOffer?.terms) {
      return round.supplierOffer.terms;
    }
  }

  const conversation = session?.conversation ?? [];

  for (let index = conversation.length - 1; index >= 0; index -= 1) {
    const event = conversation[index];

    if (event?.terms) {
      return event.terms;
    }

    if (event?.counterOffers?.[0]) {
      return event.counterOffers[0];
    }
  }

  return null;
}

function resolveReferenceOptions(session) {
  const rounds = session?.rounds ?? [];

  for (let index = rounds.length - 1; index >= 0; index -= 1) {
    const counterOffers = rounds[index]?.buyerReply?.counterOffers;

    if (counterOffers?.length > 0) {
      return counterOffers;
    }
  }

  const conversation = session?.conversation ?? [];

  for (let index = conversation.length - 1; index >= 0; index -= 1) {
    const counterOffers = conversation[index]?.counterOffers;

    if (counterOffers?.length > 0) {
      return counterOffers;
    }
  }

  return [];
}

async function resolveDraftTerms({
  bounds,
  counterOffers,
  messageDraft,
  parsedDraft,
  referenceTerms,
}) {
  if (!shouldUseAiParsing(messageDraft, parsedDraft)) {
    return parsedDraft;
  }

  try {
    const aiTerms = await parseSupplierOfferWithAi({
      supplierMessage: messageDraft.trim(),
      referenceTerms,
      counterOffers,
    });

    return {
      ...parseSupplierMessage("", bounds, aiTerms, []),
      constraints: parsedDraft.constraints,
    };
  } catch {
    return parsedDraft;
  }
}

function ConnectScreen({
  error,
  loadingDefaults,
  onStartSession,
  startingSession,
}) {
  return (
    <section className='flex min-h-screen items-center justify-center px-4'>
      <div className='w-full max-w-sm space-y-3'>
        {error && (
          <div className='rounded-2xl border border-[var(--danger-ink)]/15 bg-[var(--danger-soft)] px-4 py-3 text-sm leading-6 text-[var(--danger-ink)]'>
            {error}
          </div>
        )}

        <Button
          className='h-11 w-full'
          disabled={loadingDefaults || startingSession}
          onClick={onStartSession}
          type='button'
        >
          {loadingDefaults || startingSession ? (
            <LoaderCircle className='h-4 w-4 animate-spin' />
          ) : null}
          Connect to buyer
        </Button>
      </div>
    </section>
  );
}

export default App;
