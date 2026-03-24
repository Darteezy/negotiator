import { useState } from "react";
import { ConfigurationPage } from "@/pages/ConfigurationPage";
import { NegotiationPage } from "@/pages/NegotiationPage";
import type { BuyerPreferences } from "@/lib/types";

export default function App() {
  const [profile, setProfile] = useState<BuyerPreferences | null>(null);
  const [sessionKey, setSessionKey] = useState(0);

  function handleStart(nextProfile: BuyerPreferences) {
    setProfile(nextProfile);
    setSessionKey((key) => key + 1);
  }

  function handleReset() {
    setProfile(null);
  }

  if (!profile) {
    return <ConfigurationPage onStart={handleStart} />;
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
