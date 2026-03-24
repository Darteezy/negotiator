import { useState, useEffect } from "react";
import { ChatPage } from "@/pages/ChatPage";
import { ConfigurationPage } from "@/pages/ConfigurationPage";
import { NegotiationPage } from "@/pages/NegotiationPage";
import { AdminPage } from "@/pages/AdminPage";
import type { BuyerPreferences } from "@/lib/types";

export default function App() {
  const [profile, setProfile] = useState<BuyerPreferences | null>(null);
  const [sessionKey, setSessionKey] = useState(0);
  const [useFormMode, setUseFormMode] = useState(false);
  const [showAdmin, setShowAdmin] = useState(false);

  // Check URL for mode parameter
  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.get("admin") === "true") {
      setShowAdmin(true);
    }
    if (params.get("mode") === "form") {
      setUseFormMode(true);
    }
  }, []);

  function handleStart(nextProfile: BuyerPreferences) {
    setProfile(nextProfile);
    setSessionKey((key) => key + 1);
    // Reset URL to remove mode parameter
    window.history.replaceState({}, "", window.location.pathname);
  }

  function handleReset() {
    setProfile(null);
  }

  // Admin page (accessible via ?admin=true parameter)
  if (showAdmin) {
    return (
      <div className="relative">
        <button
          onClick={() => {
            setShowAdmin(false);
            window.history.replaceState({}, "", window.location.pathname);
          }}
          className="absolute top-4 right-4 z-10 flex items-center gap-2 rounded-full border border-[var(--line)] bg-white/90 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-[var(--ink-strong)] shadow-sm transition hover:bg-white"
        >
          Exit Admin
        </button>
        <AdminPage />
      </div>
    );
  }

  if (!profile) {
    if (useFormMode) {
      return (
        <div className="relative">
          <button
            onClick={() => {
              setUseFormMode(false);
              window.history.replaceState({}, "", window.location.pathname);
            }}
            className="absolute top-4 left-4 z-10 flex items-center gap-2 rounded-full border border-[var(--line)] bg-white/90 px-3 py-2 text-xs font-semibold uppercase tracking-wide text-[var(--ink-strong)] shadow-sm transition hover:bg-white"
          >
            ← Back to Chat Mode
          </button>
          <ConfigurationPage onStart={handleStart} />
        </div>
      );
    }

    return (
      <ChatPage
        onStart={(profile) => {
          handleStart(profile);
        }}
      />
    );
  }

  return (
    <NegotiationPage
      key={sessionKey}
      profile={profile}
      onReset={handleReset}
      onRestart={handleReset}
    />
  );
}
