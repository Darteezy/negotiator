import { cva } from 'class-variance-authority'

import { cn } from '@/lib/utils'

const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 rounded-full border text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--accent)] focus-visible:ring-offset-2 disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        primary: 'border-transparent bg-[var(--accent)] px-4 py-2.5 text-white shadow-[0_14px_30px_rgba(14,124,102,0.24)] hover:brightness-110',
        secondary: 'border-[var(--line)] bg-[var(--panel)] px-4 py-2.5 text-[var(--ink-strong)] hover:bg-[var(--accent-soft)]',
        ghost: 'border-transparent bg-transparent px-3 py-2 text-[var(--ink-muted)] hover:bg-[var(--accent-soft)] hover:text-[var(--buyer-ink)]',
      },
      size: {
        md: 'h-11',
        sm: 'h-9 px-3 text-xs',
      },
    },
    defaultVariants: {
      variant: 'primary',
      size: 'md',
    },
  },
)

export function Button({ className, size, variant, ...props }) {
  return <button className={cn(buttonVariants({ size, variant }), className)} {...props} />
}