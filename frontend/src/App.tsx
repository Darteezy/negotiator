import { useState } from "react";
import { ConfigurationPage } from "@/pages/ConfigurationPage";
import { NegotiationPage } from "@/pages/NegotiationPage";
import type { ApiNegotiationSession } from "@/lib/types";

export default function App() {
  const [session, setSession] = useState<ApiNegotiationSession | null>(null);

  function handleStart(nextSession: ApiNegotiationSession) {
    setSession(nextSession);
  }

  function handleReset() {
    setSession(null);
  }

  if (!session) {
    return <ConfigurationPage onStart={handleStart} />;
  }

  return <NegotiationPage initialSession={session} onRestart={handleReset} />;
}
