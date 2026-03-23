import { formatMoney } from "@/lib/format";

const termMetadata = [
  { key: "price", label: "Price", format: formatMoney },
  { key: "paymentDays", label: "Payment", suffix: "days" },
  { key: "deliveryDays", label: "Delivery", suffix: "days" },
  { key: "contractMonths", label: "Contract", suffix: "months" },
];

export function TermsGrid({ terms }) {
  if (!terms) {
    return null;
  }

  return (
    <div className='grid gap-1.5 sm:grid-cols-2 xl:grid-cols-2'>
      {termMetadata.map((term) => {
        const rawValue = terms[term.key];
        const renderedValue = term.format
          ? term.format(rawValue)
          : `${rawValue} ${term.suffix}`;

        return (
          <div
            key={term.key}
            className='border border-[var(--line)] bg-white/75 px-2 py-1.5 shadow-none'
          >
            <p className='app-mono text-[10px] uppercase tracking-[0.12em] text-[var(--ink-soft)]'>
              {term.label}
            </p>
            <p className='mt-1 app-mono text-[12px] font-medium text-[var(--ink-strong)]'>
              {renderedValue}
            </p>
          </div>
        );
      })}
    </div>
  );
}
