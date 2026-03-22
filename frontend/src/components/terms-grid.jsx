import { formatMoney } from '@/lib/format'

const termMetadata = [
  { key: 'price', label: 'Price', format: formatMoney },
  { key: 'paymentDays', label: 'Payment', suffix: 'days' },
  { key: 'deliveryDays', label: 'Delivery', suffix: 'days' },
  { key: 'contractMonths', label: 'Contract', suffix: 'months' },
]

export function TermsGrid({ terms }) {
  if (!terms) {
    return null
  }

  return (
    <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
      {termMetadata.map((term) => {
        const rawValue = terms[term.key]
        const renderedValue = term.format ? term.format(rawValue) : `${rawValue} ${term.suffix}`

        return (
          <div key={term.key} className="rounded-2xl border border-[var(--line)] bg-white/75 px-3 py-3 shadow-sm">
            <p className="text-[11px] uppercase tracking-[0.18em] text-[var(--ink-soft)]">{term.label}</p>
            <p className="mt-2 app-mono text-sm font-medium text-[var(--ink-strong)]">{renderedValue}</p>
          </div>
        )
      })}
    </div>
  )
}