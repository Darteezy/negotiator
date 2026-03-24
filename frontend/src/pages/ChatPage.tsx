import { useState, useRef, useEffect, useMemo } from "react";
import { ArrowLeft, RotateCcw, SendHorizontal } from "lucide-react";
import {
  parseConfigurationMessage,
  refinePreferences,
  createFullPreferences,
} from "@/lib/chatMessageParser";
import { startNegotiation, processSupplierOffer } from "@/lib/negotiationEngine";
import { computeUtility, describeUtilityLevel } from "@/lib/utilityFunction";
import type { BuyerPreferences, NegotiationState, OfferTerms, SupplierOfferInput } from "@/lib/types";
import { ChatBubble, DetectedTerms } from "@/components/ChatMessage";
import { ChatInput } from "@/components/ChatInput";
import { RobotAvatar } from "@/components/RobotAvatar";
import { OfferCard } from "@/components/OfferCard";

interface Props {
  onStart: (profile: BuyerPreferences) => void;
}

interface ConfigMessage {
  id: string;
  actor: "user" | "bot";
  content: string;
  timestamp: Date;
}

const DEFAULT_PROFILE: BuyerPreferences = {
  price: {
    preferred: 10000,
    max: 12000,
  },
  paymentDays: {
    preferred: 30,
    max: 60,
  },
  deliveryDays: {
    preferred: 10,
    max: 30,
  },
  contractMonths: {
    preferred: 12,
    min: 6,
    max: 18,
  },
  weights: {
    price: 0.45,
    paymentDays: 0.2,
    deliveryDays: 0.2,
    contractMonths: 0.15,
  },
  thresholds: {
    acceptStart: 0.8,
    acceptFloor: 0.62,
    counterStart: 0.45,
    counterFloor: 0.35,
  },
  maxRounds: 8,
};

export function ChatPage({ onStart }: Props) {
  // Configuration phase state
  const [configMessages, setConfigMessages] = useState<ConfigMessage[]>([
    {
      id: "intro",
      actor: "bot",
      content:
        "Hi! I'm your negotiation agent. Let me set up the buyer mandate first.\n\nTell me about the budget: What's your target price and maximum acceptable price? Also mention preferred payment days, delivery time, and contract length.",
      timestamp: new Date(),
    },
  ]);
  const [extracted, setExtracted] = useState<Partial<BuyerPreferences>>({});
  const [configComplete, setConfigComplete] = useState(false);

  // Negotiation phase state
  const [negotiationState, setNegotiationState] = useState<NegotiationState | null>(null);
  const [negotiationMessages, setNegotiationMessages] = useState<ConfigMessage[]>([]);
  const [submittingOffer, setSubmittingOffer] = useState(false);
  const [price, setPrice] = useState("");
  const [payment, setPayment] = useState("");
  const [delivery, setDelivery] = useState("");
  const [contract, setContract] = useState("");
  const [note, setNote] = useState("Here's my offer.");
  const [error, setError] = useState("");

  const scrollRef = useRef<HTMLDivElement | null>(null);

  // Scroll to bottom when messages change
  useEffect(() => {
    if (scrollRef.current) {
      setTimeout(() => {
        scrollRef.current?.scrollTo({
          top: scrollRef.current.scrollHeight,
          behavior: "smooth",
        });
      }, 0);
    }
  }, [configMessages, negotiationMessages]);

  // Initialize negotiation form with defaults
  useEffect(() => {
    if (negotiationState) {
      const profile = negotiationState.buyerProfile;
      setPrice(String(profile.price.preferred));
      setPayment(String(profile.paymentDays.preferred));
      setDelivery(String(profile.deliveryDays.preferred));
      setContract(String(profile.contractMonths.preferred));
    }
  }, [negotiationState]);

  // Handle configuration message
  const handleConfigMessage = (userMessage: string) => {
    // Add user message to chat
    const userMsg: ConfigMessage = {
      id: `msg-${Date.now()}-user`,
      actor: "user",
      content: userMessage,
      timestamp: new Date(),
    };
    setConfigMessages((prev) => [...prev, userMsg]);

    // Parse the message
    let parseResult;
    if (Object.keys(extracted).length === 0) {
      parseResult = parseConfigurationMessage(userMessage);
    } else {
      parseResult = refinePreferences(extracted, userMessage);
    }

    setExtracted(parseResult.extracted);

    // Determine bot response
    let botResponse = "";

    if (parseResult.complete) {
      botResponse =
        "Perfect! I've got all the details:\n\n" +
        formatExtractedSummary(parseResult.extracted) +
        "\n\nI'll use these settings for the negotiation. Click 'Start Negotiation' when ready.";
      setConfigComplete(true);
    } else if (parseResult.missing.length > 0) {
      const missingList = parseResult.missing
        .map((m) => `• ${m}`)
        .join("\n");
      botResponse =
        "Thanks! I got some info. Could you also provide:\n\n" +
        missingList +
        "\n\nExample: 'I also want payment in 30 to 60 days, delivery in 7 to 14 days, and a 12-month contract.'";
    }

    // Add bot response
    const botMsg: ConfigMessage = {
      id: `msg-${Date.now()}-bot`,
      actor: "bot",
      content: botResponse,
      timestamp: new Date(),
    };
    setConfigMessages((prev) => [...prev, botMsg]);
  };

  // Handle starting negotiation
  const handleStartNegotiation = () => {
    if (!configComplete || Object.keys(extracted).length === 0) return;

    const profile = createFullPreferences(extracted, DEFAULT_PROFILE);
    const state = startNegotiation(profile);

    setNegotiationState(state);
    setNegotiationMessages([
      {
        id: "neg-intro",
        actor: "bot",
        content: state.history[0].message,
        timestamp: new Date(),
      },
    ]);
  };

  // Handle negotiation offer submission
  const handleSubmitOffer = (event: React.FormEvent) => {
    event.preventDefault();

    if (!negotiationState) return;

    const supplierTerms: OfferTerms = {
      price: Number(price),
      paymentDays: Math.round(Number(payment)),
      deliveryDays: Math.round(Number(delivery)),
      contractMonths: Math.round(Number(contract)),
    };

    // Validate
    const anyNaN = Object.values(supplierTerms).some((v) => isNaN(v));
    if (anyNaN) {
      setError("Please fill all fields with valid numbers.");
      return;
    }

    setSubmittingOffer(true);
    setError("");

    try {
      // Add supplier message to chat
      const supplierMsg: ConfigMessage = {
        id: `neg-${Date.now()}-supplier`,
        actor: "user",
        content: note || "Here's my offer",
        timestamp: new Date(),
      };
      setNegotiationMessages((prev) => [...prev, supplierMsg]);

      // Process offer through negotiation engine
      const payload: SupplierOfferInput = {
        terms: supplierTerms,
        note,
      };

      const nextState = processSupplierOffer(negotiationState, negotiationState.buyerProfile, payload);
      setNegotiationState(nextState);

      // Add all new events to messages
      const newEvents = nextState.history.slice(negotiationState.history.length);
      for (const event of newEvents) {
        const msg: ConfigMessage = {
          id: event.id,
          actor: event.actor === "buyer" ? "bot" : "user",
          content: event.message,
          timestamp: new Date(event.at),
        };
        setNegotiationMessages((prev) => [...prev, msg]);
      }

      // Reset form
      setPrice(String(nextState.buyerProfile.price.preferred));
      setPayment(String(nextState.buyerProfile.paymentDays.preferred));
      setDelivery(String(nextState.buyerProfile.deliveryDays.preferred));
      setContract(String(nextState.buyerProfile.contractMonths.preferred));
      setNote("Following up on the buyer feedback.");
    } finally {
      setSubmittingOffer(false);
    }
  };

  // Calculate utility for preview
  const supplierForm: OfferTerms | null = useMemo(() => {
    const parsed = {
      price: Number(price),
      paymentDays: Math.round(Number(payment)),
      deliveryDays: Math.round(Number(delivery)),
      contractMonths: Math.round(Number(contract)),
    };
    const anyNaN = Object.values(parsed).some((v) => isNaN(v));
    return anyNaN ? null : parsed;
  }, [price, payment, delivery, contract]);

  const utilityPreview = supplierForm && negotiationState ? computeUtility(supplierForm, negotiationState.buyerProfile) : null;

  // Render configuration phase
  if (!configComplete || !negotiationState) {
    return (
      <div className="flex h-screen flex-col overflow-hidden bg-[var(--page-bg)]">
        {/* Header */}
        <div className="shrink-0 border-b border-[var(--line)] bg-[var(--panel)] px-4 py-4 sm:px-6">
          <div className="mx-auto max-w-2xl flex items-center justify-between">
            <div>
              <h1 className="text-2xl font-bold text-[var(--ink-strong)]">Negotiation Setup</h1>
              <p className="mt-1 text-sm text-[var(--ink-muted)]">Describe your buyer mandate in natural language</p>
            </div>
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => window.location.href = "?mode=form"}
                className="flex items-center gap-1 rounded-full border border-[var(--line)] bg-[var(--accent)] px-3 py-2 text-xs font-semibold uppercase tracking-wide text-[var(--page-bg)] transition hover:bg-[var(--accent)]/90"
                title="Switch to traditional form-based setup"
              >
                Use forms
              </button>
              <button
                type="button"
                onClick={() => window.location.href = "?admin=true"}
                className="flex items-center gap-1 rounded-full border border-[var(--line)] bg-[var(--accent)] px-3 py-2 text-xs font-semibold uppercase tracking-wide text-[var(--page-bg)] transition hover:bg-[var(--accent)]/90"
                title="Developer/Admin panel"
              >
                ⚙
              </button>
            </div>
          </div>
        </div>

        {/* Chat area */}
        <div
          ref={scrollRef}
          className="min-h-0 flex-1 overflow-y-auto space-y-4 bg-[var(--page-bg)] px-4 py-4 sm:px-6"
        >
          <div className="mx-auto max-w-2xl space-y-4">
            {configMessages.map((msg) => (
              <ChatBubble key={msg.id} actor={msg.actor}>
                <p className="whitespace-pre-wrap">{msg.content}</p>
              </ChatBubble>
            ))}
          </div>
        </div>

        {/* Input area */}
        <div className="shrink-0 border-t border-[var(--line)] bg-[var(--panel)] px-4 py-4 sm:px-6">
          <div className="mx-auto max-w-2xl space-y-3">
            {!configComplete ? (
              <ChatInput
                placeholder="E.g., Target price is 10k, max 12k. 30-60 days payment, 7-14 days delivery, 12 months contract."
                onSubmit={handleConfigMessage}
              />
            ) : (
              <div className="space-y-3">
                <div className="rounded-2xl border border-[var(--accent)] bg-[var(--accent-soft)] px-4 py-3">
                  <p className="text-sm font-semibold text-[var(--accent)]">Configuration complete!</p>
                  <p className="mt-1 text-xs text-[var(--accent)]">{formatExtractedSummary(extracted)}</p>
                </div>

                <button
                  type="button"
                  onClick={handleStartNegotiation}
                  className="w-full rounded-full bg-[var(--accent)] px-4 py-3 font-bold text-[var(--page-bg)] shadow-lg shadow-[var(--accent)]/30 transition hover:-translate-y-0.5 uppercase tracking-wide"
                >
                  <SendHorizontal className="mr-2 inline h-4 w-4" />
                  Start Negotiation
                </button>
              </div>
            )}
          </div>
        </div>
      </div>
    );
  }

  // Render negotiation phase
  const lastBuyerEvent = [...negotiationState.history]
    .reverse()
    .find((evt) => evt.actor === "buyer");

  return (
    <div className="grid min-h-screen grid-cols-1 gap-4 bg-[var(--page-bg)] p-4 lg:grid-cols-[1fr_380px] sm:p-6">
      {/* Main chat area */}
      <div className="flex min-h-0 flex-col rounded-2xl border border-[var(--line)] bg-white shadow-lg">
        {/* Header */}
        <div className="shrink-0 border-b border-[var(--line)] px-4 py-4 sm:px-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-xl font-bold text-[var(--ink-strong)]">Negotiation</h2>
              <p className="text-xs text-[var(--ink-muted)] mt-1">
                Round {negotiationState.round} •{" "}
                {lastBuyerEvent?.utility ? `Utility ${Math.round(lastBuyerEvent.utility * 100)}% (${describeUtilityLevel(lastBuyerEvent.utility)})` : "Pending"}
              </p>
            </div>
            <button
              type="button"
              onClick={() => window.location.reload()}
              className="flex items-center gap-2 rounded-full border border-[var(--line)] bg-white px-3 py-2 text-xs font-semibold uppercase tracking-wide text-[var(--ink-strong)] shadow-sm transition hover:bg-[var(--page-bg)]"
            >
              <RotateCcw className="h-4 w-4" />
              Reset
            </button>
          </div>
        </div>

        {/* Chat history */}
        <div
          ref={scrollRef}
          className="min-h-0 flex-1 overflow-y-auto space-y-4 px-4 py-4 sm:px-6"
        >
          {negotiationMessages.slice(0, 1).map((msg) => (
            <ChatBubble key={msg.id} actor={msg.actor}>
              <p className="text-xs text-white">{msg.content}</p>
            </ChatBubble>
          ))}

          {/* Render negotiation events as alternating offers */}
          {negotiationState.history.map((event, idx) => (
            <OfferCard
              key={event.id}
              title={event.title}
              actor={event.actor === "buyer" ? "buyer" : "supplier"}
              terms={event.terms}
              message={event.message}
              utility={event.utility}
              breakdown={event.breakdown}
              align={event.actor === "supplier" ? "right" : "left"}
              highlight={event.decision === "ACCEPT"}
            />
          ))}

          {negotiationState.status === "accepted" && (
            <div className="rounded-2xl border border-[var(--accent)] bg-[var(--accent-soft)] px-4 py-3 text-sm font-semibold text-[var(--accent)]">
              ✓ Agreement reached!
            </div>
          )}

          {negotiationState.status === "rejected" && (
            <div className="rounded-2xl border border-red-300 bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">
              ✗ Negotiation rejected.
            </div>
          )}
        </div>

        {/* Offer input form (only if negotiation active) */}
        {negotiationState.status === "pending" && (
          <div className="shrink-0 border-t border-[var(--line)] bg-[var(--panel)] px-4 py-4 sm:px-6">
            <form onSubmit={handleSubmitOffer} className="space-y-3">
              {error && (
                <div className="rounded-xl border border-red-300 bg-red-50 px-3 py-2 text-xs font-semibold text-red-700">
                  {error}
                </div>
              )}

              <div className="grid grid-cols-4 gap-2">
                <label className="flex flex-col gap-1 rounded-xl border border-[var(--line)] bg-white px-2.5 py-2">
                  <span className="text-[10px] font-bold uppercase tracking-wide text-[var(--ink-muted)]">Price (€)</span>
                  <input
                    type="number"
                    value={price}
                    onChange={(e) => setPrice(e.target.value)}
                    step="0.01"
                    className="border-none bg-transparent text-sm font-semibold text-[var(--ink-strong)] outline-none"
                  />
                </label>

                <label className="flex flex-col gap-1 rounded-xl border border-[var(--line)] bg-white px-2.5 py-2">
                  <span className="text-[10px] font-bold uppercase tracking-wide text-[var(--ink-muted)]">Payment (d)</span>
                  <input
                    type="number"
                    value={payment}
                    onChange={(e) => setPayment(e.target.value)}
                    step="1"
                    className="border-none bg-transparent text-sm font-semibold text-[var(--ink-strong)] outline-none"
                  />
                </label>

                <label className="flex flex-col gap-1 rounded-xl border border-[var(--line)] bg-white px-2.5 py-2">
                  <span className="text-[10px] font-bold uppercase tracking-wide text-[var(--ink-muted)]">Delivery (d)</span>
                  <input
                    type="number"
                    value={delivery}
                    onChange={(e) => setDelivery(e.target.value)}
                    step="1"
                    className="border-none bg-transparent text-sm font-semibold text-[var(--ink-strong)] outline-none"
                  />
                </label>

                <label className="flex flex-col gap-1 rounded-xl border border-[var(--line)] bg-white px-2.5 py-2">
                  <span className="text-[10px] font-bold uppercase tracking-wide text-[var(--ink-muted)]">Contract (m)</span>
                  <input
                    type="number"
                    value={contract}
                    onChange={(e) => setContract(e.target.value)}
                    step="1"
                    className="border-none bg-transparent text-sm font-semibold text-[var(--ink-strong)] outline-none"
                  />
                </label>
              </div>

              <textarea
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="Add a note..."
                rows={2}
                className="w-full rounded-xl border border-[var(--line)] bg-white px-3 py-2 text-xs outline-none focus:border-[var(--accent)]"
              />

              {utilityPreview && (
                <div className="text-right text-xs font-semibold text-[var(--ink-muted)]">
                  Utility: {Math.round(utilityPreview.utility * 100)}%
                </div>
              )}

              <button
                type="submit"
                disabled={submittingOffer || negotiationState.status !== "pending"}
                className="w-full rounded-full bg-[var(--accent)] px-4 py-2.5 font-bold text-white shadow-md transition disabled:opacity-50 uppercase tracking-wide text-sm"
              >
                Send Offer
              </button>
            </form>
          </div>
        )}
      </div>

      {/* Sidebar */}
      <div className="hidden lg:flex flex-col gap-4">
        <div className="rounded-2xl border border-[var(--line)] bg-white p-4 shadow-md">
          <p className="text-xs font-semibold uppercase tracking-wide text-[var(--ink-muted)]">Buyer Profile</p>
          <div className="mt-3 space-y-2 text-xs">
            <div className="flex justify-between">
              <span className="text-[var(--ink-muted)]">Price:</span>
              <span className="font-semibold text-[var(--ink-strong)]">
                €{negotiationState.buyerProfile.price.preferred} (max €{negotiationState.buyerProfile.price.max})
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-[var(--ink-muted)]">Payment:</span>
              <span className="font-semibold text-[var(--ink-strong)]">
                {negotiationState.buyerProfile.paymentDays.preferred}d (max {negotiationState.buyerProfile.paymentDays.max}d)
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-[var(--ink-muted)]">Delivery:</span>
              <span className="font-semibold text-[var(--ink-strong)]">
                {negotiationState.buyerProfile.deliveryDays.preferred}d (max {negotiationState.buyerProfile.deliveryDays.max}d)
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-[var(--ink-muted)]">Contract:</span>
              <span className="font-semibold text-[var(--ink-strong)]">
                {negotiationState.buyerProfile.contractMonths.preferred}m ({negotiationState.buyerProfile.contractMonths.min}-{negotiationState.buyerProfile.contractMonths.max}m)
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

function formatExtractedSummary(extracted: Partial<BuyerPreferences>): string {
  const parts: string[] = [];

  if (extracted.price) {
    parts.push(`Price: €${extracted.price.preferred} (max €${extracted.price.max})`);
  }
  if (extracted.paymentDays) {
    parts.push(`Payment: ${extracted.paymentDays.preferred}-${extracted.paymentDays.max} days`);
  }
  if (extracted.deliveryDays) {
    parts.push(`Delivery: ${extracted.deliveryDays.preferred}-${extracted.deliveryDays.max} days`);
  }
  if (extracted.contractMonths) {
    parts.push(
      `Contract: ${extracted.contractMonths.min}-${extracted.contractMonths.max} months`
    );
  }

  return parts.join(" • ");
}
