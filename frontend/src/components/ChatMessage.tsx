import React from "react";
import { RobotAvatar } from "./RobotAvatar";

export interface ChatMessage {
  id: string;
  actor: "user" | "bot";
  content: string;
  timestamp: Date;
  action?: {
    label: string;
    onClick: () => void;
  };
}

interface ChatMessageProps {
  message: ChatMessage;
}

export function ChatMessageComponent({ message }: ChatMessageProps) {
  const isUser = message.actor === "user";

  return (
    <div className={`flex gap-3 ${isUser ? "justify-end" : "justify-start"}`}>
      <div
        className={`max-w-[70%] rounded-2xl px-4 py-3 ${
          isUser
            ? "bg-[var(--accent)] text-white"
            : "border border-[var(--line)] bg-white text-[var(--ink-strong)]"
        }`}
      >
        <p className="whitespace-pre-wrap text-sm leading-6">{message.content}</p>

        {message.action && (
          <button
            onClick={message.action.onClick}
            className={`mt-3 rounded-lg px-3 py-2 text-xs font-semibold uppercase tracking-wide transition ${
              isUser
                ? "bg-white/20 hover:bg-white/30 text-white"
                : "bg-[var(--accent-soft)] text-[var(--accent)] hover:bg-[var(--accent-soft)]/80"
            }`}
          >
            {message.action.label}
          </button>
        )}
      </div>
    </div>
  );
}

export function ChatBubble({
  actor,
  children,
  className = "",
  action,
}: {
  actor: "user" | "bot";
  children: React.ReactNode;
  className?: string;
  action?: {
    label: string;
    onClick: () => void;
  };
}) {
  const isUser = actor === "user";

  return (
    <div className={`flex gap-3 ${isUser ? "justify-end" : "justify-start"} ${className}`}>
      {!isUser && <RobotAvatar size="sm" />}
      <div
        className={`max-w-[70%] rounded-2xl px-4 py-3 ${
          isUser
            ? "bg-[var(--accent)] text-white"
            : "border border-[var(--line)] bg-[var(--panel)] text-[var(--ink-strong)]"
        }`}
      >
        <div className="text-sm leading-6">{children}</div>

        {action && (
          <button
            onClick={action.onClick}
            className={`mt-3 rounded-lg px-3 py-2 text-xs font-semibold uppercase tracking-wide transition ${
              isUser
                ? "bg-white/20 hover:bg-white/30"
                : "bg-[var(--accent)]/20 text-[var(--accent)] hover:bg-[var(--accent)]/30"
            }`}
          >
            {action.label}
          </button>
        )}
      </div>
    </div>
  );
}

/**
 * Detected terms display (badges shown below input during typing)
 */
export function DetectedTerms({
  terms,
}: {
  terms: {
    price?: string;
    paymentDays?: string;
    deliveryDays?: string;
    contractMonths?: string;
  };
}) {
  const values = Object.entries(terms).filter(([, v]) => v !== undefined);

  if (values.length === 0) {
    return null;
  }

  const labels: Record<string, string> = {
    price: "Price",
    paymentDays: "Payment",
    deliveryDays: "Delivery",
    contractMonths: "Contract",
  };

  return (
    <div className="mt-2 flex flex-wrap gap-2">
      {values.map(([key, value]) => (
        <span
          key={key}
          className="rounded-full border border-[var(--line)] bg-[var(--accent-soft)] px-3 py-1.5 text-[12px] font-medium text-[var(--accent)]"
        >
          {labels[key]}: {value}
        </span>
      ))}
    </div>
  );
}
