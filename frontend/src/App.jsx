import { useEffect, useRef, useState } from "react";
import {
  PanelRightClose,
  PanelRightOpen,
  RotateCcw,
} from "lucide-react";

import {
  fetchSessionDefaults,
  openSimulationStream,
  parseSupplierOfferWithAi,
  startSession,
  submitSupplierOffer,
} from "@/api/negotiation";
import { ConversationPane } from "@/components/conversation-pane";
import { DebugPane } from "@/components/debug-pane";
import { OfferComposer } from "@/components/offer-composer";
import { SessionSetupScreen } from "@/components/session-setup-screen";
import { Button } from "@/components/ui/button";
import { parseSupplierMessage, shouldUseAiParsing } from "@/lib/chat-offer";
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
  const [runningSimulation, setRunningSimulation] = useState(false);
  const [submittingOffer, setSubmittingOffer] = useState(false);
  const [submittedSupplierMessages, setSubmittedSupplierMessages] = useState(
    [],
  );
  const [pendingSupplierMessage, setPendingSupplierMessage] = useState("");
  const [showDebug, setShowDebug] = useState(false);
  const [sessionMode, setSessionMode] = useState("interactive");
  const [simulationMeta, setSimulationMeta] = useState(null);
  const [error, setError] = useState("");
  const simulationStreamRef = useRef(null);

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

  useEffect(() => {
    return () => {
      simulationStreamRef.current?.close();
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
      simulationStreamRef.current?.close();
      setStartingSession(true);
      setError("");
      setSessionMode("interactive");
      setSimulationMeta(null);
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
    setSimulationMeta(null);

    if (sessionMode === "simulation") {
      await handleStartSimulation();
      return;
    }

    await handleStartSession();
  }

  async function handleStartSimulation() {
    try {
      simulationStreamRef.current?.close();
      setRunningSimulation(true);
      setError("");
      const startedAt = new Date();
      const maxRounds = Math.min(
        Number(sessionConfig?.maxRounds ?? defaults?.maxRounds ?? 6),
        6,
      );
      const requestedStrategy =
        sessionConfig?.strategy ?? defaults?.defaultStrategy ?? "MESO";
      setSessionMode("simulation");
      setSimulationMeta({
        anomalyCount: 0,
        anomalies: [],
        elapsedLabel: null,
        state: "running",
      });
      setSession(
        buildStreamingSimulationSession({
          maxRounds,
          requestedStrategy,
          startedAt,
        }),
      );
      setSubmittedSupplierMessages([]);
      setMessageDraft("");
      setPendingSupplierMessage(
        "AI supplier is preparing the opening offer...",
      );

      const eventSource = openSimulationStream({
        strategy: requestedStrategy,
        maxRounds,
      });
      simulationStreamRef.current = eventSource;

      eventSource.addEventListener("started", (event) => {
        const payload = JSON.parse(event.data);
        setSession((current) =>
          applySimulationStarted(current, payload, {
            maxRounds,
            requestedStrategy,
            startedAt,
          }),
        );
      });

      eventSource.addEventListener("round", (event) => {
        const payload = JSON.parse(event.data);
        setPendingSupplierMessage(
          payload.closed ? "" : "Waiting for the next supplier reply...",
        );
        setSession((current) => applySimulationRound(current, payload));
      });

      eventSource.addEventListener("completed", (event) => {
        const payload = JSON.parse(event.data);
        setPendingSupplierMessage("");
        setSimulationMeta({
          anomalyCount: payload.anomalies?.length ?? 0,
          anomalies: payload.anomalies ?? [],
          elapsedLabel: formatDurationLabel(startedAt, new Date()),
          state: "completed",
        });
        setSession((current) => finalizeSimulationSession(current, payload));
        eventSource.close();
        simulationStreamRef.current = null;
        setRunningSimulation(false);
      });

      eventSource.addEventListener("failed", (event) => {
        const payload = JSON.parse(event.data);
        setPendingSupplierMessage("");
        setError(payload.message ?? "Simulation failed.");
        setSimulationMeta((current) => ({
          anomalyCount: current?.anomalyCount ?? 0,
          anomalies: current?.anomalies ?? [],
          elapsedLabel: formatDurationLabel(startedAt, new Date()),
          state: "failed",
        }));
        setSession((current) =>
          current
            ? {
                ...current,
                closed: true,
                status: "REJECTED",
              }
            : current,
        );
        eventSource.close();
        simulationStreamRef.current = null;
        setRunningSimulation(false);
      });

      eventSource.onerror = () => {
        eventSource.close();
        simulationStreamRef.current = null;
        setPendingSupplierMessage("");
        setRunningSimulation(false);
      };
    } catch (simulationError) {
      setError(simulationError.message);
      setPendingSupplierMessage("");
      simulationStreamRef.current?.close();
    }
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
        const rangeMessages = resolvedDraft.outOfBounds
          .map((fieldKey) => formatTermRange(fieldKey, bounds))
          .filter(Boolean);

        setError(
          rangeMessages.length > 0
            ? `Detected values are outside the allowed range. ${rangeMessages.join(" ")}`
            : `Detected values are outside the allowed range for ${resolvedDraft.outOfBounds.join(", ")}.`,
        );
        setPendingSupplierMessage("");
        return;
      }

      const nextSession = await submitSupplierOffer(session.id, {
        ...resolvedDraft.terms,
        supplierMessage: messageDraft.trim(),
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
          <SessionSetupScreen
            availableStrategies={defaults?.availableStrategies ?? []}
            bounds={defaults?.bounds ?? null}
            config={sessionConfig}
            error={error}
            loadingDefaults={loadingDefaults}
            onFieldChange={handleSessionTermChange}
            onReset={handleResetSessionConfig}
            onSessionSettingChange={handleSessionSettingChange}
            onStartSimulation={handleStartSimulation}
            onStartSession={handleStartSession}
            runningSimulation={runningSimulation}
            startingSession={startingSession}
          />
        ) : (
          <div className='flex min-h-0 flex-1 flex-col overflow-hidden'>
            {sessionMode === "simulation" && simulationMeta ? (
              <div className='border-b border-[var(--line)] bg-[var(--accent-soft)] px-3 py-2 text-sm text-[var(--ink-strong)] sm:px-4'>
                <div className='flex flex-wrap items-center justify-between gap-2'>
                  <span>
                    {simulationMeta.state === "running"
                      ? "Simulation is live. Watch the bots negotiate round by round."
                      : `Browser demo finished in ${simulationMeta.elapsedLabel}. Showing the full AI-vs-buyer transcript.`}
                  </span>
                  <span className='app-mono text-[11px] uppercase tracking-[0.12em] text-[var(--ink-soft)]'>
                    {simulationMeta.anomalyCount} anomalies
                  </span>
                </div>
              </div>
            ) : null}
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
                      onClick={() => setShowDebug((d) => !d)}
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
                      {sessionMode === "simulation"
                        ? "Run Demo Again"
                        : "New Negotiation"}
                    </Button>
                  </div>
                </div>
                {sessionMode === "simulation" &&
                simulationMeta?.anomalies?.length ? (
                  <div className='mt-2 flex flex-wrap gap-2 text-xs text-[var(--ink-muted)]'>
                    {simulationMeta.anomalies.map((anomaly) => (
                      <span
                        key={anomaly}
                        className='rounded-full border border-[var(--line)] bg-white px-2 py-1'
                      >
                        {anomaly}
                      </span>
                    ))}
                  </div>
                ) : null}
              </div>
            ) : (
              <div className='shrink-0 border-t border-[var(--line)] bg-[var(--panel)]'>
                <div className='flex items-center justify-end px-3 py-1 lg:hidden'>
                  <Button
                    onClick={() => setShowDebug((d) => !d)}
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
                  disabled={sessionMode === "simulation" || submittingOffer}
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

function buildStreamingSimulationSession(options) {
  return {
    id: null,
    closed: false,
    status: "PENDING",
    strategy: options.requestedStrategy,
    currentRound: 0,
    maxRounds: options.maxRounds,
    bounds: null,
    conversation: [
      {
        actor: "system",
        at: options.startedAt.toISOString(),
      },
    ],
    rounds: [],
    strategyHistory: [],
  };
}

function applySimulationStarted(current, payload, options) {
  if (!current) {
    return buildStreamingSimulationSession(options);
  }

  return {
    ...current,
    id: payload.sessionId,
    strategy: payload.strategy ?? current.strategy,
    maxRounds: payload.maxRounds ?? current.maxRounds,
  };
}

function applySimulationRound(current, payload) {
  if (!current) {
    return current;
  }

  const round = payload.round;
  const existingRounds = current.rounds ?? [];
  const nextRound = buildSimulationRound(round, payload, current);
  const previousRound = existingRounds[existingRounds.length - 1] ?? null;
  const nextStrategyHistory = [...(current.strategyHistory ?? [])];

  if (
    nextRound.buyerReply?.switchTrigger &&
    nextRound.buyerReply?.strategyUsed
  ) {
    nextStrategyHistory.push({
      at: nextRound.buyerReply?.decidedAt ?? nextRound.supplierOffer.at,
      roundNumber: round.round,
      nextStrategy: nextRound.buyerReply.strategyUsed,
      trigger: nextRound.buyerReply.switchTrigger,
      rationale:
        nextRound.buyerReply.strategyRationale ??
        `Simulation switched from ${previousRound?.buyerReply?.strategyUsed ?? current.strategy} to ${nextRound.buyerReply.strategyUsed}.`,
    });
  }

  return {
    ...current,
    id: payload.sessionId ?? current.id,
    currentRound: payload.currentRound ?? current.currentRound,
    closed: payload.closed ?? current.closed,
    status: payload.status ?? current.status,
    strategy: payload.strategy ?? current.strategy,
    rounds: [...existingRounds, nextRound],
    strategyHistory: nextStrategyHistory,
  };
}

function finalizeSimulationSession(current, result) {
  if (!current) {
    return current;
  }

  return {
    ...current,
    id: result.sessionId ?? current.id,
    closed: true,
    status: result.finalStatus ?? current.status,
    strategy: result.finalStrategy ?? current.strategy,
    currentRound: result.roundsPlayed ?? current.currentRound,
  };
}

function buildSimulationRound(round, payload, session) {
  const supplierAt = new Date().toISOString();
  const buyerAt = new Date().toISOString();
  const buyerTerms = pickBuyerTerms(round);
  const supplierTerms = normalizeSimulationTerms(round.supplierOffer);

  return {
    roundNumber: round.round,
    supplierOffer: {
      at: supplierAt,
      message: summarizeSimulationSupplierMessage(round),
      rawMessage: round.supplierMessage,
      terms: supplierTerms,
    },
    buyerReply: round.buyer
      ? {
          decidedAt: buyerAt,
          decision: round.buyer.decision,
          explanation: round.buyer.explanation,
          resultingStatus: resultStatusForDecision(
            round.buyer.decision,
            payload.closed ? payload.status : null,
          ),
          counterOffer: buyerTerms,
          terms: buyerTerms,
          counterOffers: (round.buyer.counterOffers ?? []).map(
            normalizeSimulationTerms,
          ),
          evaluation: round.buyer.evaluation,
          strategyUsed:
            round.buyer.strategyUsed ?? payload.strategy ?? session.strategy,
          strategyRationale: round.buyer.strategyRationale ?? null,
          switchTrigger: round.buyer.switchTrigger ?? null,
        }
      : null,
  };
}

function pickBuyerTerms(round) {
  const counterOffers = round.buyer?.counterOffers ?? [];
  if (round.buyer?.decision === "ACCEPT") {
    return normalizeSimulationTerms(round.supplierOffer);
  }

  return normalizeSimulationTerms(counterOffers[0] ?? null);
}

function normalizeSimulationTerms(terms) {
  if (!terms) {
    return null;
  }

  return {
    price: terms.price,
    paymentDays: terms.paymentDays,
    deliveryDays: terms.deliveryDays,
    contractMonths: terms.contractMonths,
  };
}

function summarizeSimulationSupplierMessage(round) {
  const message = round.supplierMessage?.trim();
  const terms = normalizeSimulationTerms(round.supplierOffer);

  if (!message) {
    return formatSimulationTerms(terms);
  }

  if (message.startsWith("AI_ERROR:") || message.startsWith("ENGINE_ERROR:")) {
    return message;
  }

  return formatSimulationTerms(terms);
}

function formatSimulationTerms(terms) {
  if (!terms) {
    return "Supplier submitted a proposal.";
  }

  return `I propose price ${formatSimulationPrice(terms.price)}, payment in ${terms.paymentDays} days, delivery in ${terms.deliveryDays} days, and a ${terms.contractMonths} month contract.`;
}

function formatSimulationPrice(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return "—";
  }

  return `€${Number(value).toFixed(2)}`;
}

function resultStatusForDecision(decision, finalStatus) {
  if (decision === "ACCEPT") {
    return "ACCEPTED";
  }

  if (decision === "REJECT") {
    return "REJECTED";
  }

  return finalStatus === "PENDING" ? "PENDING" : "COUNTERED";
}

function formatDurationLabel(startedAt, finishedAt) {
  const totalSeconds = Math.max(
    1,
    Math.round((finishedAt.getTime() - startedAt.getTime()) / 1000),
  );

  if (totalSeconds < 60) {
    return `${totalSeconds}s`;
  }

  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return seconds === 0 ? `${minutes}m` : `${minutes}m ${seconds}s`;
}

export default App;
