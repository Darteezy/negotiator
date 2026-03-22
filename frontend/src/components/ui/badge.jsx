import { cva } from 'class-variance-authority'

import { cn } from '@/lib/utils'

const badgeVariants = cva(
  'inline-flex items-center rounded-full px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.18em]',
  {
    variants: {
      tone: {
        neutral: 'bg-[var(--system-soft)] text-[var(--system-ink)]',
        supplier: 'bg-[var(--supplier-soft)] text-[var(--supplier-ink)]',
        buyer: 'bg-[var(--buyer-soft)] text-[var(--buyer-ink)]',
        success: 'bg-[var(--buyer-soft)] text-[var(--buyer-ink)]',
        danger: 'bg-[var(--danger-soft)] text-[var(--danger-ink)]',
      },
    },
    defaultVariants: {
      tone: 'neutral',
    },
  },
)

export function Badge({ className, tone, ...props }) {
  return <span className={cn(badgeVariants({ tone }), className)} {...props} />
}