import React, { useState, useCallback } from "react";
import { SendHorizontal } from "lucide-react";
import { extractPreviewTerms } from "@/lib/chatMessageParser";
import { DetectedTerms } from "./ChatMessage";

interface ChatInputProps {
  onSubmit: (message: string) => void;
  disabled?: boolean;
  placeholder?: string;
}

export function ChatInput({
  onSubmit,
  disabled = false,
  placeholder = "Describe your requirements (price, payment days, delivery time, contract length)...",
}: ChatInputProps) {
  const [value, setValue] = useState("");
  const [preview, setPreview] = useState<Record<string, string | undefined>>({});

  const handleChange = useCallback(
    (event: React.ChangeEvent<HTMLTextAreaElement>) => {
      const newValue = event.target.value;
      setValue(newValue);

      // Real-time preview extraction
      if (newValue.trim()) {
        const extracted = extractPreviewTerms(newValue);
        setPreview(extracted);
      } else {
        setPreview({});
      }
    },
    []
  );

  const handleSubmit = useCallback(
    (event: React.FormEvent) => {
      event.preventDefault();

      if (value.trim() && !disabled) {
        onSubmit(value.trim());
        setValue("");
        setPreview({});
      }
    },
    [value, disabled, onSubmit]
  );

  const handleKeyDown = (event: React.KeyboardEvent<HTMLTextAreaElement>) => {
    // Submit on Enter (but not Shift+Enter)
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      handleSubmit(event as any);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="w-full space-y-2">
      <div className="relative">
        <label className="sr-only">Message</label>
        <textarea
          value={value}
          onChange={handleChange}
          onKeyDown={handleKeyDown}
          disabled={disabled}
          placeholder={placeholder}
          rows={3}
          className="w-full rounded-2xl border border-[var(--line)] bg-white px-4 py-3 text-sm leading-6 text-[var(--ink-strong)] outline-none transition disabled:opacity-50 disabled:cursor-not-allowed focus:border-[var(--accent)]"
        />
      </div>

      <div className="flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
        <DetectedTerms terms={preview} />

        <button
          type="submit"
          disabled={disabled || !value.trim()}
          className="inline-flex items-center gap-2 rounded-full bg-[var(--accent)] px-5 py-2.5 text-sm font-semibold text-white shadow-md shadow-[var(--accent)]/20 transition disabled:opacity-50 disabled:cursor-not-allowed hover:shadow-lg hover:shadow-[var(--accent)]/30"
        >
          <SendHorizontal className="h-4 w-4" />
          Send
        </button>
      </div>
    </form>
  );
}
