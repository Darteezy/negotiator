import { useState, useMemo } from "react";
import { ChevronDown, ChevronUp, RotateCcw, Settings } from "lucide-react";
import type { BuyerPreferences, NegotiationState, OfferTerms } from "@/lib/types";
import { computeUtility } from "@/lib/utilityFunction";
import { startNegotiation, processSupplierOffer } from "@/lib/negotiationEngine";
import { RobotAvatar } from "@/components/RobotAvatar";

/**
 * Admin/Developer Page for Negotiation System
 *
 * This page is NOT visible to end users and provides:
 * - Bot Configuration (buyer preferences, weights, thresholds)
 * - Negotiation Engine Controls (current state, decision inspection)
 * - Strategy Management (rule-based decision making)
 * - Scoring/Utility System (visualization)
 * - Advanced Features (MESO analysis, history)
 */

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

export function AdminPage() {
  const [profile, setProfile] = useState<BuyerPreferences>(DEFAULT_PROFILE);
  const [negotiationState, setNegotiationState] = useState<NegotiationState | null>(null);
  const [expandedSection, setExpandedSection] = useState<string | null>("config");
  const [testOffer, setTestOffer] = useState<OfferTerms>({
    price: 10500,
    paymentDays: 45,
    deliveryDays: 12,
    contractMonths: 12,
  });

  // Initialize negotiation with current profile
  const handleStartNegotiation = () => {
    const state = startNegotiation(profile);
    setNegotiationState(state);
  };

  // Test an offer
  const handleTestOffer = () => {
    if (!negotiationState) return;
    const nextState = processSupplierOffer(negotiationState, profile, {
      terms: testOffer,
      note: "Test offer from admin",
    });
    setNegotiationState(nextState);
  };

  // Calculate utility for test offer
  const testUtility = useMemo(() => {
    return computeUtility(testOffer, profile);
  }, [testOffer, profile]);

  // Update profile field
  const updateProfileField = (path: string, value: any) => {
    setProfile((prev) => {
      const keys = path.split(".");
      const updated = JSON.parse(JSON.stringify(prev));
      let current = updated;
      for (let i = 0; i < keys.length - 1; i++) {
        current = current[keys[i]];
      }
      current[keys[keys.length - 1]] = value;
      return updated;
    });
  };

  return (
    <div className="min-h-screen bg-[var(--page-bg)] text-[var(--ink-strong)]">
      {/* Header */}
      <div className="border-b border-[var(--line)] bg-[var(--panel)] px-6 py-4 shadow-sm">
        <div className="mx-auto max-w-7xl">
          <div className="flex items-center gap-4">
            <RobotAvatar size="md" />
            <div>
              <h1 className="text-2xl font-bold">Admin Panel</h1>
              <p className="text-sm text-[var(--ink-muted)]">
                Negotiation Engine Configuration & Control Center
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Main Content */}
      <div className="mx-auto max-w-7xl px-6 py-6">
        <div className="grid gap-6 lg:grid-cols-3">
          {/* Left: Configuration & Testing */}
          <div className="lg:col-span-2 space-y-6">
            {/* Bot Configuration */}
            <Section
              title="Bot Configuration"
              description="Set buyer preferences and constraints"
              isOpen={expandedSection === "config"}
              onToggle={() => setExpandedSection(expandedSection === "config" ? null : "config")}
            >
              <div className="space-y-6">
                {/* Price */}
                <ConfigField
                  label="Price (EUR)"
                  description="Buyer's price preferences"
                >
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-semibold text-[var(--ink-muted)] mb-1">
                        Preferred
                      </label>
                      <input
                        type="number"
                        value={profile.price.preferred}
                        onChange={(e) =>
                          updateProfileField("price.preferred", parseFloat(e.target.value))
                        }
                        className="w-full rounded border border-[var(--line)] bg-[var(--page-bg)]/70 px-3 py-2 text-sm text-[var(--ink-strong)]"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-[var(--ink-muted)] mb-1">
                        Max (Hard Limit)
                      </label>
                      <input
                        type="number"
                        value={profile.price.max}
                        onChange={(e) =>
                          updateProfileField("price.max", parseFloat(e.target.value))
                        }
                        className="w-full rounded border border-[var(--line)] bg-[var(--page-bg)]/70 px-3 py-2 text-sm text-[var(--ink-strong)]"
                      />
                    </div>
                  </div>
                </ConfigField>

                {/* Payment Days */}
                <ConfigField
                  label="Payment Days"
                  description="Preferred and max acceptable payment terms"
                >
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-semibold text-[var(--ink-muted)] mb-1">
                        Preferred
                      </label>
                      <input
                        type="number"
                        value={profile.paymentDays.preferred}
                        onChange={(e) =>
                          updateProfileField("paymentDays.preferred", parseInt(e.target.value))
                        }
                        className="w-full rounded border border-[var(--line)] px-3 py-2 text-sm text-[var(--ink-strong)] bg-[var(--page-bg)]/70"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-[var(--ink-muted)] mb-1">
                        Max
                      </label>
                      <input
                        type="number"
                        value={profile.paymentDays.max}
                        onChange={(e) =>
                          updateProfileField("paymentDays.max", parseInt(e.target.value))
                        }
                        className="w-full rounded border border-[var(--line)] px-3 py-2 text-sm text-[var(--ink-strong)] bg-[var(--page-bg)]/70"
                      />
                    </div>
                  </div>
                </ConfigField>

                {/* Delivery Days */}
                <ConfigField
                  label="Delivery Days"
                  description="Expected and max acceptable delivery time"
                >
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-xs font-semibold text-[var(--ink-muted)] mb-1">
                        Preferred
                      </label>
                      <input
                        type="number"
                        value={profile.deliveryDays.preferred}
                        onChange={(e) =>
                          updateProfileField("deliveryDays.preferred", parseInt(e.target.value))
                        }
                        className="w-full rounded border border-[var(--line)] px-3 py-2 text-sm text-[var(--ink-strong)] bg-[var(--page-bg)]/70"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-[var(--ink-muted)] mb-1">
                        Max
                      </label>
                      <input
                        type="number"
                        value={profile.deliveryDays.max}
                        onChange={(e) =>
                          updateProfileField("deliveryDays.max", parseInt(e.target.value))
                        }
                        className="w-full rounded border border-[var(--line)] px-3 py-2 text-sm text-[var(--ink-strong)] bg-[var(--page-bg)]/70"
                      />
                    </div>
                  </div>
                </ConfigField>

                {/* Contract Months */}
                <ConfigField
                  label="Contract Length (Months)"
                  description="Contract term range"
                >
                  <div className="grid grid-cols-3 gap-4">
                    <div>
                      <label className="block text-xs font-semibold text-[var(--ink-muted)] mb-1">
                        Min
                      </label>
                      <input
                        type="number"
                        value={profile.contractMonths.min}
                        onChange={(e) =>
                          updateProfileField("contractMonths.min", parseInt(e.target.value))
                        }
                        className="w-full rounded border border-[var(--line)] px-3 py-2 text-sm text-[var(--ink-strong)] bg-[var(--page-bg)]/70"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-[var(--ink-muted)] mb-1">
                        Preferred
                      </label>
                      <input
                        type="number"
                        value={profile.contractMonths.preferred}
                        onChange={(e) =>
                          updateProfileField(
                            "contractMonths.preferred",
                            parseInt(e.target.value)
                          )
                        }
                        className="w-full rounded border border-[var(--line)] px-3 py-2 text-sm text-[var(--ink-strong)] bg-[var(--page-bg)]/70"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-[var(--ink-muted)] mb-1">
                        Max
                      </label>
                      <input
                        type="number"
                        value={profile.contractMonths.max}
                        onChange={(e) =>
                          updateProfileField("contractMonths.max", parseInt(e.target.value))
                        }
                        className="w-full rounded border border-[var(--line)] px-3 py-2 text-sm text-[var(--ink-strong)] bg-[var(--page-bg)]/70"
                      />
                    </div>
                  </div>
                </ConfigField>
              </div>
            </Section>

            {/* Weights & Thresholds */}
            <Section
              title="Scoring Strategy"
              description="Adjust weights and decision thresholds"
              isOpen={expandedSection === "weights"}
              onToggle={() => setExpandedSection(expandedSection === "weights" ? null : "weights")}
            >
              <div className="space-y-6">
                {/* Weights */}
                <div>
                  <h4 className="text-sm font-bold text-[var(--ink-strong)] mb-3">
                    Parameter Weights (importance)
                  </h4>
                  <div className="space-y-3">
                    {[
                      { key: "price", label: "Price" },
                      { key: "paymentDays", label: "Payment Terms" },
                      { key: "deliveryDays", label: "Delivery Speed" },
                      { key: "contractMonths", label: "Contract Length" },
                    ].map(({ key, label }) => (
                      <div key={key}>
                        <div className="flex justify-between mb-1">
                          <label className="text-xs font-semibold">{label}</label>
                          <span className="text-xs font-mono bg-[var(--page-bg)] px-2 py-1 rounded">
                            {(profile.weights[key as keyof typeof profile.weights] * 100).toFixed(
                              0
                            )}
                            %
                          </span>
                        </div>
                        <input
                          type="range"
                          min="0"
                          max="1"
                          step="0.01"
                          value={profile.weights[key as keyof typeof profile.weights]}
                          onChange={(e) =>
                            updateProfileField(`weights.${key}`, parseFloat(e.target.value))
                          }
                          className="w-full"
                        />
                      </div>
                    ))}
                  </div>
                </div>

                {/* Thresholds */}
                <div>
                  <h4 className="text-sm font-bold text-[var(--ink-strong)] mb-3">
                    Decision Thresholds (utility score 0-1)
                  </h4>
                  <div className="space-y-3 text-sm">
                    <div>
                      <label className="block text-xs font-semibold mb-1">
                        Accept Start Threshold ({(profile.thresholds.acceptStart * 100).toFixed(0)}%)
                      </label>
                      <input
                        type="range"
                        min="0"
                        max="1"
                        step="0.01"
                        value={profile.thresholds.acceptStart}
                        onChange={(e) =>
                          updateProfileField("thresholds.acceptStart", parseFloat(e.target.value))
                        }
                        className="w-full"
                      />
                      <p className="text-xs text-[var(--ink-muted)] mt-1">
                        Initial offer must reach this utility to be accepted
                      </p>
                    </div>

                    <div>
                      <label className="block text-xs font-semibold mb-1">
                        Accept Floor ({(profile.thresholds.acceptFloor * 100).toFixed(0)}%)
                      </label>
                      <input
                        type="range"
                        min="0"
                        max="1"
                        step="0.01"
                        value={profile.thresholds.acceptFloor}
                        onChange={(e) =>
                          updateProfileField("thresholds.acceptFloor", parseFloat(e.target.value))
                        }
                        className="w-full"
                      />
                      <p className="text-xs text-[var(--ink-muted)] mt-1">
                        Minimum utility in final rounds
                      </p>
                    </div>

                    <div>
                      <label className="block text-xs font-semibold mb-1">
                        Counter Start Threshold ({(profile.thresholds.counterStart * 100).toFixed(0)}%)
                      </label>
                      <input
                        type="range"
                        min="0"
                        max="1"
                        step="0.01"
                        value={profile.thresholds.counterStart}
                        onChange={(e) =>
                          updateProfileField(
                            "thresholds.counterStart",
                            parseFloat(e.target.value)
                          )
                        }
                        className="w-full"
                      />
                      <p className="text-xs text-[var(--ink-muted)] mt-1">
                        Generate counteroffers above this threshold
                      </p>
                    </div>

                    <div>
                      <label className="block text-xs font-semibold mb-1">
                        Counter Floor ({(profile.thresholds.counterFloor * 100).toFixed(0)}%)
                      </label>
                      <input
                        type="range"
                        min="0"
                        max="1"
                        step="0.01"
                        value={profile.thresholds.counterFloor}
                        onChange={(e) =>
                          updateProfileField(
                            "thresholds.counterFloor",
                            parseFloat(e.target.value)
                          )
                        }
                        className="w-full"
                      />
                      <p className="text-xs text-[var(--ink-muted)] mt-1">
                        Reject offers below this floor
                      </p>
                    </div>
                  </div>
                </div>

                {/* Max Rounds */}
                <ConfigField label="Max Negotiation Rounds">
                  <input
                    type="number"
                    value={profile.maxRounds}
                    onChange={(e) =>
                      updateProfileField("maxRounds", parseInt(e.target.value))
                    }
                    className="w-full rounded border border-[var(--line)] px-3 py-2 text-sm text-[var(--ink-strong)] bg-[var(--page-bg)]/70"
                  />
                </ConfigField>
              </div>
            </Section>

            {/* Test Offer & Scoring */}
            <Section
              title="Utility Scoring Test"
              description="Test offers against current configuration"
              isOpen={expandedSection === "test"}
              onToggle={() => setExpandedSection(expandedSection === "test" ? null : "test")}
            >
              <div className="space-y-4">
                <div className="grid grid-cols-4 gap-3">
                  <div>
                    <label className="block text-xs font-semibold mb-1">Price</label>
                    <input
                      type="number"
                      value={testOffer.price}
                      onChange={(e) =>
                        setTestOffer((prev) => ({
                          ...prev,
                          price: parseFloat(e.target.value),
                        }))
                      }
                      className="w-full rounded border border-[var(--line)] px-2 py-1 text-sm text-[var(--ink-strong)] bg-[var(--page-bg)]/70"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold mb-1">Payment Days</label>
                    <input
                      type="number"
                      value={testOffer.paymentDays}
                      onChange={(e) =>
                        setTestOffer((prev) => ({
                          ...prev,
                          paymentDays: parseInt(e.target.value),
                        }))
                      }
                      className="w-full rounded border border-[var(--line)] px-2 py-1 text-sm text-[var(--ink-strong)] bg-[var(--page-bg)]/70"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold mb-1">Delivery Days</label>
                    <input
                      type="number"
                      value={testOffer.deliveryDays}
                      onChange={(e) =>
                        setTestOffer((prev) => ({
                          ...prev,
                          deliveryDays: parseInt(e.target.value),
                        }))
                      }
                      className="w-full rounded border border-[var(--line)] px-2 py-1 text-sm text-[var(--ink-strong)] bg-[var(--page-bg)]/70"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold mb-1">Contract Months</label>
                    <input
                      type="number"
                      value={testOffer.contractMonths}
                      onChange={(e) =>
                        setTestOffer((prev) => ({
                          ...prev,
                          contractMonths: parseInt(e.target.value),
                        }))
                      }
                      className="w-full rounded border border-[var(--line)] px-2 py-1 text-sm text-[var(--ink-strong)] bg-[var(--page-bg)]/70"
                    />
                  </div>
                </div>

                {/* Utility Breakdown */}
                <div className="rounded-lg border border-[var(--line)] bg-[var(--panel)] p-4">
                  <h4 className="text-sm font-bold mb-4">Utility Breakdown</h4>
                  <div className="space-y-3">
                    <UtilityBar
                      label="Price"
                      score={testUtility.breakdown.price.score}
                      weight={profile.weights.price}
                      weighted={testUtility.breakdown.price.weighted}
                    />
                    <UtilityBar
                      label="Payment Days"
                      score={testUtility.breakdown.paymentDays.score}
                      weight={profile.weights.paymentDays}
                      weighted={testUtility.breakdown.paymentDays.weighted}
                    />
                    <UtilityBar
                      label="Delivery Days"
                      score={testUtility.breakdown.deliveryDays.score}
                      weight={profile.weights.deliveryDays}
                      weighted={testUtility.breakdown.deliveryDays.weighted}
                    />
                    <UtilityBar
                      label="Contract Months"
                      score={testUtility.breakdown.contractMonths.score}
                      weight={profile.weights.contractMonths}
                      weighted={testUtility.breakdown.contractMonths.weighted}
                    />

                    <div className="border-t border-[var(--line)] pt-3 mt-3">
                      <div className="flex justify-between items-center">
                        <span className="font-bold">Overall Utility</span>
                        <div className="flex items-center gap-3">
                          <div className="w-32 h-3 rounded-full bg-[var(--page-bg)] overflow-hidden">
                            <div
                              className="h-full bg-[var(--accent)]"
                              style={{
                                width: `${testUtility.utility * 100}%`,
                              }}
                            />
                          </div>
                          <span className="font-mono font-bold text-lg">
                            {(testUtility.utility * 100).toFixed(1)}%
                          </span>
                        </div>
                      </div>
                    </div>

                    {testUtility.violations.length > 0 && (
                      <div className="rounded bg-red-50 border border-red-300 p-3 mt-3">
                        <p className="text-xs font-bold text-red-700 mb-2">⚠ Violations:</p>
                        <ul className="text-xs text-red-600 space-y-1">
                          {testUtility.violations.map((violation, idx) => (
                            <li key={idx}>• {violation}</li>
                          ))}
                        </ul>
                      </div>
                    )}
                  </div>
                </div>

                {!negotiationState && (
                  <button
                    onClick={handleStartNegotiation}
                    className="w-full rounded-lg bg-[var(--accent)] px-4 py-2 font-semibold text-[var(--page-bg)] transition hover:bg-[var(--accent)]/90"
                  >
                    Start Live Negotiation with This Config
                  </button>
                )}
              </div>
            </Section>
          </div>

          {/* Right: State & Decisions */}
          <div className="space-y-6">
            {/* Negotiation State */}
            <Section
              title="Negotiation State"
              description={
                negotiationState
                  ? `Round ${negotiationState.round} • ${negotiationState.status}`
                  : "Not started"
              }
              isOpen={expandedSection === "state"}
              onToggle={() => setExpandedSection(expandedSection === "state" ? null : "state")}
            >
              {!negotiationState ? (
                <p className="text-sm text-[var(--ink-muted)]">
                  Start a negotiation to view state and decision history.
                </p>
              ) : (
                <div className="space-y-3">
                  <div>
                    <p className="text-xs font-semibold text-[var(--ink-muted)]">Round</p>
                    <p className="text-lg font-bold">{negotiationState.round}</p>
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-[var(--ink-muted)]">Status</p>
                    <p className="text-lg font-bold capitalize">{negotiationState.status}</p>
                  </div>
                  {negotiationState.lastUtility !== undefined && (
                    <div>
                      <p className="text-xs font-semibold text-[var(--ink-muted)]">Last Utility</p>
                      <p className="text-lg font-bold">
                        {(negotiationState.lastUtility * 100).toFixed(1)}%
                      </p>
                    </div>
                  )}
                  <div>
                    <p className="text-xs font-semibold text-[var(--ink-muted)] mb-2">
                      Negotiation History
                    </p>
                    <div className="space-y-2 max-h-64 overflow-y-auto">
                      {negotiationState.history.map((event) => (
                        <div
                          key={event.id}
                          className="text-xs rounded bg-[var(--page-bg)] p-2 border border-[var(--line)]"
                        >
                          <p className="font-bold">{event.title}</p>
                          <p className="text-[var(--ink-muted)]">{event.actor}</p>
                          {event.decision && (
                            <p className="font-semibold text-[var(--accent)] mt-1">
                              Decision: {event.decision}
                            </p>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>

                  <button
                    onClick={() => setNegotiationState(null)}
                    className="w-full rounded-lg bg-red-100 px-3 py-2 text-sm font-semibold text-red-700 transition hover:bg-red-200"
                  >
                    Reset Negotiation
                  </button>
                </div>
              )}
            </Section>

            {/* MESO Offers Analysis */}
            {negotiationState && negotiationState.history.length > 0 && (
              <Section
                title="MESO Counteroffers"
                description="Multiple Equivalent Simultaneous Offers"
                isOpen={expandedSection === "meso"}
                onToggle={() =>
                  setExpandedSection(expandedSection === "meso" ? null : "meso")
                }
              >
                <div className="space-y-3">
                  {negotiationState.history
                    .filter((e) => e.counterOffers && e.counterOffers.length > 0)
                    .map((event) => (
                      <div key={event.id} className="text-xs space-y-2">
                        <p className="font-bold text-[var(--ink-strong)]">{event.title}</p>
                        {event.counterOffers!.map((offer, idx) => {
                          const utility = computeUtility(offer, profile);
                          return (
                            <div
                              key={idx}
                              className="rounded bg-[var(--page-bg)] border border-[var(--line)] p-2"
                            >
                              <div className="flex justify-between items-start mb-1">
                                <span className="font-semibold">Option {idx + 1}</span>
                                <span className="font-mono font-bold">
                                  {(utility.utility * 100).toFixed(0)}%
                                </span>
                              </div>
                              <div className="text-[var(--ink-muted)] space-y-0.5">
                                <p>€{offer.price.toFixed(2)} | {offer.paymentDays}d payment</p>
                                <p>
                                  {offer.deliveryDays}d delivery | {offer.contractMonths}m contract
                                </p>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    ))}
                </div>
              </Section>
            )}
          </div>
        </div>

        {/* Footer */}
        <div className="mt-12 rounded-lg border border-[var(--line)] bg-[var(--panel)] p-6">
          <h3 className="font-bold text-sm mb-2">Negotiation Engine Architecture</h3>
          <div className="grid gap-4 text-xs text-[var(--ink-muted)] lg:grid-cols-2">
            <div>
              <p className="font-semibold text-[var(--ink-strong)] mb-1">Decision Flow</p>
              <p>Offer → Utility Scoring → Threshold Check → Decision (ACCEPT/COUNTER/REJECT)</p>
            </div>
            <div>
              <p className="font-semibold text-[var(--ink-strong)] mb-1">Scoring Strategy</p>
              <p>
                Normalized dimension scores × weights = overall utility. Thresholds adjust dynamically
                over rounds (concession schedule).
              </p>
            </div>
            <div>
              <p className="font-semibold text-[var(--ink-strong)] mb-1">MESO Generation</p>
              <p>
                Bot generates up to 3 offers with equal utility but different term combinations
                (margin-focused, speed-focused, balance).
              </p>
            </div>
            <div>
              <p className="font-semibold text-[var(--ink-strong)] mb-1">Concession Schedule</p>
              <p>
                Thresholds decay over rounds to encourage final agreement while protecting buyer's
                minimum acceptable utility.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// ============================================================================
// COMPONENTS
// ============================================================================

function Section({
  title,
  description,
  isOpen,
  onToggle,
  children,
}: {
  title: string;
  description?: string;
  isOpen: boolean;
  onToggle: () => void;
  children: React.ReactNode;
}) {
  return (
    <div className="rounded-lg border border-[var(--line)] bg-[var(--panel)] overflow-hidden shadow-sm">
      <button
        onClick={onToggle}
        className="w-full px-4 py-3 flex items-center justify-between hover:bg-[var(--page-bg)]/50 transition"
      >
        <div className="text-left">
          <h3 className="font-bold text-sm text-[var(--ink-strong)]">{title}</h3>
          {description && (
            <p className="text-xs text-[var(--ink-muted)] mt-0.5">{description}</p>
          )}
        </div>
        {isOpen ? (
          <ChevronUp className="h-4 w-4 text-[var(--ink-muted)]" />
        ) : (
          <ChevronDown className="h-4 w-4 text-[var(--ink-muted)]" />
        )}
      </button>
      {isOpen && <div className="border-t border-[var(--line)] px-4 py-4">{children}</div>}
    </div>
  );
}

function ConfigField({
  label,
  description,
  children,
}: {
  label: string;
  description?: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label className="block text-sm font-semibold text-[var(--ink-strong)] mb-1">
        {label}
      </label>
      {description && (
        <p className="text-xs text-[var(--ink-muted)] mb-2">{description}</p>
      )}
      {children}
    </div>
  );
}

function UtilityBar({
  label,
  score,
  weight,
  weighted,
}: {
  label: string;
  score: number;
  weight: number;
  weighted: number;
}) {
  return (
    <div>
      <div className="flex justify-between text-xs mb-1">
        <span className="font-semibold">{label}</span>
        <div className="flex gap-2 font-mono">
          <span>score {(score * 100).toFixed(0)}%</span>
          <span>× {(weight * 100).toFixed(0)}% weight</span>
          <span className="font-bold">= {(weighted * 100).toFixed(0)}%</span>
        </div>
      </div>
      <div className="h-2 rounded-full bg-[var(--page-bg)] overflow-hidden">
        <div
          className="h-full bg-[var(--accent)]/70"
          style={{
            width: `${Math.min(weighted * 100, 100)}%`,
          }}
        />
      </div>
    </div>
  );
}
