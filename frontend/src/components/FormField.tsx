import type { ChangeEvent } from "react";

interface Props {
  label: string;
  value: string;
  onChange: (value: string) => void;
  suffix?: string;
  prefix?: string;
  helper?: string;
  type?: "number" | "text";
  min?: number;
  max?: number;
  step?: string;
}

export function FormField({
  label,
  value,
  onChange,
  suffix,
  prefix,
  helper,
  type = "number",
  min,
  max,
  step,
}: Props) {
  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    onChange(event.target.value);
  };

  return (
    <label className='flex flex-col gap-1 rounded-2xl border border-[var(--line)] bg-white/10 px-4 py-3 shadow-sm shadow-black/10'>
      <div className='flex items-center justify-between text-sm font-semibold text-[var(--ink-strong)]'>
        <span>{label}</span>
        {suffix ? (
          <span className='text-xs font-medium text-[var(--ink-muted)]'>
            {suffix}
          </span>
        ) : null}
      </div>
      <div className='flex items-center gap-2 rounded-xl bg-[var(--panel)]/90 px-3 py-2 ring-1 ring-[var(--line)]'>
        {prefix ? (
          <span className='text-sm font-semibold text-[var(--ink-muted)]'>
            {prefix}
          </span>
        ) : null}
        <input
          className='w-full border-none bg-transparent text-base font-semibold text-[var(--ink-strong)] outline-none'
          type={type}
          inputMode={type === "number" ? "decimal" : undefined}
          value={value}
          onChange={handleChange}
          min={min}
          max={max}
          step={step}
        />
      </div>
      {helper ? (
        <p className='text-xs leading-tight text-[var(--ink-soft)]'>{helper}</p>
      ) : null}
    </label>
  );
}
