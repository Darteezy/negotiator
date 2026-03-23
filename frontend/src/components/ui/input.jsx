import { cn } from '@/lib/utils'

export function Input({ className, ...props }) {
  return (
    <input
      className={cn(
        'h-12 w-full rounded-2xl border border-[var(--line)] bg-[var(--page-bg)] px-4 text-sm text-[var(--ink-strong)] shadow-inner outline-none transition placeholder:text-[var(--ink-soft)] focus:border-[var(--accent)] focus:bg-white',
        className,
      )}
      {...props}
    />
  )
}