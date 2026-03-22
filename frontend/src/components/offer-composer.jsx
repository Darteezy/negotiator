import { LoaderCircle, Play, SendHorizontal } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";

const fields = [
  { key: "price", label: "Price", step: "0.01" },
  { key: "paymentDays", label: "Payment days", step: "1" },
  { key: "deliveryDays", label: "Delivery days", step: "1" },
  { key: "contractMonths", label: "Contract months", step: "1" },
];

export function OfferComposer({
  bounds,
  disabled,
  draft,
  onChange,
  onStartSession,
  onSubmit,
  session,
  startingSession,
  submittingOffer,
}) {
  return (
    <Card className='border-[var(--line)] bg-[var(--panel)]/96 backdrop-blur'>
      <CardHeader>
        <CardTitle className='text-xl tracking-[-0.03em]'>
          Offer composer
        </CardTitle>
        <CardDescription className='text-sm leading-6 text-[var(--ink-muted)]'>
          Supplier offers are submitted as real negotiation terms. The current
          backend does not parse natural-language chat into deal values yet, so
          the form stays structured by design.
        </CardDescription>
      </CardHeader>

      <CardContent className='space-y-4'>
        {!session && (
          <Button
            className='w-full'
            onClick={onStartSession}
            type='button'
            disabled={startingSession}
          >
            {startingSession ? (
              <LoaderCircle className='h-4 w-4 animate-spin' />
            ) : (
              <Play className='h-4 w-4' />
            )}
            Start negotiation session
          </Button>
        )}

        <form className='space-y-4' onSubmit={onSubmit}>
          <div className='grid gap-4 sm:grid-cols-2'>
            {fields.map((field) => (
              <label key={field.key} className='space-y-2 text-sm'>
                <span className='text-[13px] font-semibold uppercase tracking-[0.2em] text-[var(--ink-soft)]'>
                  {field.label}
                </span>
                <Input
                  disabled={!session || disabled}
                  min={minimumValue(bounds, field.key)}
                  max={maximumValue(bounds, field.key)}
                  onChange={(event) =>
                    onChange({ ...draft, [field.key]: event.target.value })
                  }
                  required
                  step={field.step}
                  type='number'
                  value={draft?.[field.key] ?? ""}
                />
              </label>
            ))}
          </div>

          {bounds && (
            <div className='rounded-2xl border border-[var(--line)] bg-[var(--page-bg)] px-4 py-4 text-sm leading-7 text-[var(--ink-muted)]'>
              <p className='mb-2 text-[13px] font-semibold uppercase tracking-[0.2em] text-[var(--ink-soft)]'>
                Allowed range
              </p>
              <ul className='grid gap-1 sm:grid-cols-2'>
                <li>
                  Price: {bounds.minPrice} to {bounds.maxPrice}
                </li>
                <li>
                  Payment: {bounds.minPaymentDays} to {bounds.maxPaymentDays}{" "}
                  days
                </li>
                <li>
                  Delivery: {bounds.minDeliveryDays} to {bounds.maxDeliveryDays}{" "}
                  days
                </li>
                <li>
                  Contract: {bounds.minContractMonths} to{" "}
                  {bounds.maxContractMonths} months
                </li>
              </ul>
            </div>
          )}

          <Button
            className='w-full'
            disabled={!session || disabled || submittingOffer}
            type='submit'
          >
            {submittingOffer ? (
              <LoaderCircle className='h-4 w-4 animate-spin' />
            ) : (
              <SendHorizontal className='h-4 w-4' />
            )}
            Submit supplier offer
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

function minimumValue(bounds, key) {
  if (!bounds) {
    return undefined;
  }

  return {
    price: bounds.minPrice,
    paymentDays: bounds.minPaymentDays,
    deliveryDays: bounds.minDeliveryDays,
    contractMonths: bounds.minContractMonths,
  }[key];
}

function maximumValue(bounds, key) {
  if (!bounds) {
    return undefined;
  }

  return {
    price: bounds.maxPrice,
    paymentDays: bounds.maxPaymentDays,
    deliveryDays: bounds.maxDeliveryDays,
    contractMonths: bounds.maxContractMonths,
  }[key];
}
