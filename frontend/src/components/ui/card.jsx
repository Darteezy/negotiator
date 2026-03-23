import { cn } from '@/lib/utils'

export function Card({ className, ...props }) {
  return <section className={cn('rounded-[28px] border shadow-[0_18px_44px_rgba(29,42,47,0.08)]', className)} {...props} />
}

export function CardHeader({ className, ...props }) {
  return <div className={cn('flex flex-col p-6', className)} {...props} />
}

export function CardTitle({ className, ...props }) {
  return <h2 className={cn('text-xl font-semibold text-[var(--ink-strong)]', className)} {...props} />
}

export function CardDescription({ className, ...props }) {
  return <p className={cn('text-sm text-[var(--ink-muted)]', className)} {...props} />
}

export function CardContent({ className, ...props }) {
  return <div className={cn('px-6 pb-6', className)} {...props} />
}