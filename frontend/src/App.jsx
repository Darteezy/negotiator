import { useEffect, useState } from "react";
import { PanelRightClose, PanelRightOpen, RotateCcw } from "lucide-react";

import {
  fetchSessionDefaults,
  startSession,
  submitSupplierOffer,
} from "@/api/negotiation";
import { ConversationPane } from "@/components/conversation-pane";
import { DebugPane } from "@/components/debug-pane";
import { OfferComposer } from "@/components/offer-composer";
import { SessionSetupScreen } from "@/components/session-setup-screen";
import { Button } from "@/components/ui/button";
import { parseSupplierMessage } from "@/lib/chat-offer";
import {
  buildStartSessionPayload,
  createSessionConfig,
  formatTermRange,
  validateSessionConfig,
} from "@/lib/session-config";

function App() {
  const [defaults, setDefaults] = useState(null);
  const [session, setSession] = useState(null);
  const [sessionConfig, setSessionConfig] = useState(null);
  const [messageDraft, setMessageDraft] = useState("");
  const [loadingDefaults, setLoadingDefaults] = useState(true);
  const [startingSession, setStartingSession] = useState(false);
  const [submittingOffer, setSubmittingOffer] = useState(false);
  const [submittedSupplierMessages, setSubmittedSupplierMessages] = useState(
    [],
  );
  const [pendingSupplierMessage, setPendingSupplierMessage] = useState("");
  const [showDebug, setShowDebug] = useState(false);
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
        setSessionConfig(createSessionConfig(nextDefaults));
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
    if (!defaults || !sessionConfig) {
      return;
    }

    const configErrors = validateSessionConfig(sessionConfig, defaults.bounds);
    if (configErrors.length > 0) {
      setError(configErrors[0]);
      return;
    }

    try {
      setStartingSession(true);
      setError("");
      const nextSession = await startSession(
        buildStartSessionPayload(sessionConfig, defaults),
      );
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

  function handleSessionTermChange(offerType, field, value) {
    setSessionConfig((current) =>
      current
        ? {
            ...current,
            [offerType]: {
              ...current[offerType],
              [field]: value,
            },
          }
        : current,
    );
    setError("");
  }

  function handleSessionSettingChange(field, value) {
    setSessionConfig((current) =>
      current
        ? {
            ...current,
            [field]: value,
          }
        : current,
    );
    setError("");
  }

  function handleResetSessionConfig() {
    if (!defaults) {
      return;
    }

    setSessionConfig(createSessionConfig(defaults));
    setError("");
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

      if (!parsedDraft.complete) {
        setError(
          `Include ${parsedDraft.missingFields.join(", ")} in the supplier message.`,
        );
        setPendingSupplierMessage("");
        return;
      }

      if (parsedDraft.outOfBounds.length > 0) {
        const rangeMessages = parsedDraft.outOfBounds
          .map((fieldKey) => formatTermRange(fieldKey, bounds))
          .filter(Boolean);

        setError(
          rangeMessages.length > 0
            ? `Detected values are outside the allowed range. ${rangeMessages.join(" ")}`
            : `Detected values are outside the allowed range for ${parsedDraft.outOfBounds.join(", ")}.`,
        );
        setPendingSupplierMessage("");
        return;
      }

      const nextSession = await submitSupplierOffer(session.id, {
        ...parsedDraft.terms,
        supplierMessage: messageDraft.trim(),
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
          <SessionSetupScreen
            bounds={defaults?.bounds ?? null}
            config={sessionConfig}
            error={error}
            loadingDefaults={loadingDefaults}
            onFieldChange={handleSessionTermChange}
            onReset={handleResetSessionConfig}
            onSessionSettingChange={handleSessionSettingChange}
            onStartSession={handleStartSession}
            startingSession={startingSession}
          />
        ) : (
          <div className='flex min-h-0 flex-1 flex-col overflow-hidden'>
            <div className='flex min-h-0 flex-1'>
              <div
                className={`min-h-0 flex-1 ${showDebug ? "hidden lg:flex" : "flex"}`}
              >
                <ConversationPane
                  loading={loadingDefaults}
                  pendingSupplierMessage={pendingSupplierMessage}
                  session={session}
                  supplierMessages={submittedSupplierMessages}
                />
              </div>
              <div
                className={`min-h-0 flex-1 border-l border-[var(--line)] lg:max-w-[38%] ${showDebug ? "flex" : "hidden lg:flex"}`}
              >
                <DebugPane
                  pendingSupplierMessage={pendingSupplierMessage}
                  session={session}
                  supplierMessages={submittedSupplierMessages}
                />
              </div>
            </div>
            {session.closed ? (
              <div className='shrink-0 border-t border-[var(--line)] bg-[var(--panel)] px-3 py-3 sm:px-4'>
                <div className='flex items-center justify-between'>
                  <span className='text-sm text-[var(--ink-muted)]'>
                    Negotiation{" "}
                    {session.status === "ACCEPTED"
                      ? "accepted"
                      : session.status === "REJECTED"
                        ? "rejected"
                        : "ended"}
                    .
                  </span>
                  <div className='flex items-center gap-2'>
                    <Button
                      className='lg:hidden'
                      onClick={() => setShowDebug((current) => !current)}
                      size='sm'
                      variant='ghost'
                    >
                      {showDebug ? (
                        <PanelRightClose className='h-4 w-4' />
                      ) : (
                        <PanelRightOpen className='h-4 w-4' />
                      )}
                      <span className='ml-1 text-xs'>
                        {showDebug ? "Chat" : "Debug"}
                      </span>
                    </Button>
                    <Button onClick={handleRestartSession} variant='outline'>
                      <RotateCcw className='mr-2 h-4 w-4' />
                      New Negotiation
                    </Button>
                  </div>
                </div>
              </div>
            ) : (
              <div className='shrink-0 border-t border-[var(--line)] bg-[var(--panel)]'>
                <div className='flex items-center justify-end px-3 py-1 lg:hidden'>
                  <Button
                    onClick={() => setShowDebug((current) => !current)}
                    size='sm'
                    variant='ghost'
                  >
                    {showDebug ? (
                      <PanelRightClose className='h-4 w-4' />
                    ) : (
                      <PanelRightOpen className='h-4 w-4' />
                    )}
                    <span className='ml-1 text-xs'>
                      {showDebug ? "Chat" : "Debug"}
                    </span>
                  </Button>
                </div>
                <OfferComposer
                  bounds={bounds}
                  disabled={submittingOffer}
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

export default App;
