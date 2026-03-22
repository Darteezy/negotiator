const fieldDefinitions = {
  price: {
    label: "price",
    patterns: [
      /(?:price|rate|cost)\D{0,12}(?:€|eur|euro|euros|\$|usd)?\s*(\d+(?:\.\d+)?)/i,
      /(?:€|eur|euro|euros|\$|usd)\s*(\d+(?:\.\d+)?)(?=[^.]*\b(?:price|rate|cost)\b)/i,
    ],
  },
  paymentDays: {
    label: "payment days",
    patterns: [
      /(?:payment|pay(?:ment)?\s+terms?)\D{0,12}(\d+)/i,
      /(\d+)\s*days?(?=[^.]*\bpayment\b)/i,
    ],
  },
  deliveryDays: {
    label: "delivery days",
    patterns: [
      /(?:delivery|deliver(?:y)?)\D{0,12}(\d+)/i,
      /(\d+)\s*days?(?=[^.]*\bdeliver(?:y)?\b)/i,
    ],
  },
  contractMonths: {
    label: "contract months",
    patterns: [
      /(?:contract|term|commitment)\D{0,12}(\d+)/i,
      /(\d+)\s*months?(?=[^.]*\b(?:contract|term|commitment)\b)/i,
    ],
  },
};

const relativeAdjustments = {
  price: [
    {
      pattern:
        /(?:price\D{0,12})?(\d+(?:\.\d+)?)\s*(?:€|eur|euro|euros|\$|usd|dollars?)\s*(higher|more|above|up)/i,
      apply: (baseValue, amount) => Number(baseValue) + Number(amount),
    },
    {
      pattern:
        /(?:price\D{0,12})?(\d+(?:\.\d+)?)\s*(?:€|eur|euro|euros|\$|usd|dollars?)\s*(lower|less|below|down)/i,
      apply: (baseValue, amount) => Number(baseValue) - Number(amount),
    },
    {
      pattern:
        /(?:increase|raise)\D{0,12}(?:price\D{0,12})?by\D{0,4}(\d+(?:\.\d+)?)/i,
      apply: (baseValue, amount) => Number(baseValue) + Number(amount),
    },
    {
      pattern:
        /(?:decrease|reduce|lower)\D{0,12}(?:price\D{0,12})?by\D{0,4}(\d+(?:\.\d+)?)/i,
      apply: (baseValue, amount) => Number(baseValue) - Number(amount),
    },
  ],
  paymentDays: [
    {
      pattern:
        /(?:payment|pay(?:ment)?\s+terms?)\D{0,12}(\d+)\s*days?\s*(longer|more)/i,
      apply: (baseValue, amount) => Number(baseValue) + Number(amount),
    },
    {
      pattern:
        /(?:payment|pay(?:ment)?\s+terms?)\D{0,12}(\d+)\s*days?\s*(shorter|less|fewer)/i,
      apply: (baseValue, amount) => Number(baseValue) - Number(amount),
    },
  ],
  deliveryDays: [
    {
      pattern:
        /(?:delivery|deliver(?:y)?)\D{0,12}(\d+)\s*days?\s*(longer|later|more)/i,
      apply: (baseValue, amount) => Number(baseValue) + Number(amount),
    },
    {
      pattern:
        /(?:delivery|deliver(?:y)?)\D{0,12}(\d+)\s*days?\s*(shorter|earlier|faster|less|fewer)/i,
      apply: (baseValue, amount) => Number(baseValue) - Number(amount),
    },
  ],
  contractMonths: [
    {
      pattern:
        /(?:contract|term|commitment)\D{0,12}(\d+)\s*months?\s*(longer|more)/i,
      apply: (baseValue, amount) => Number(baseValue) + Number(amount),
    },
    {
      pattern:
        /(?:contract|term|commitment)\D{0,12}(\d+)\s*months?\s*(shorter|less|fewer)/i,
      apply: (baseValue, amount) => Number(baseValue) - Number(amount),
    },
  ],
};

export function parseSupplierMessage(message, bounds) {
  const normalizedMessage = message.trim();
  const referenceTerms = arguments[2] ?? null;
  const counterOffers = arguments[3] ?? [];
  const optionIndex = detectOptionReference(normalizedMessage, counterOffers);
  const baseTerms =
    optionIndex === null ? referenceTerms : counterOffers[optionIndex];
  const detectedTerms = {};

  for (const [key, definition] of Object.entries(fieldDefinitions)) {
    for (const pattern of definition.patterns) {
      const match = normalizedMessage.match(pattern);

      if (match) {
        detectedTerms[key] =
          key === "price" ? Number(match[1]) : Math.round(Number(match[1]));
        break;
      }
    }
  }

  for (const [key, adjustments] of Object.entries(relativeAdjustments)) {
    if (baseTerms?.[key] === undefined) {
      continue;
    }

    for (const adjustment of adjustments) {
      const match = normalizedMessage.match(adjustment.pattern);

      if (match) {
        detectedTerms[key] = Math.round(
          adjustment.apply(baseTerms[key], Number(match[1])),
        );
        break;
      }
    }
  }

  const terms = baseTerms ? { ...baseTerms, ...detectedTerms } : detectedTerms;

  const missingFields = Object.entries(fieldDefinitions)
    .filter(([key]) => terms[key] === undefined || Number.isNaN(terms[key]))
    .map(([, definition]) => definition.label);

  const outOfBounds = bounds
    ? Object.entries({
        price: [bounds.minPrice, bounds.maxPrice],
        paymentDays: [bounds.minPaymentDays, bounds.maxPaymentDays],
        deliveryDays: [bounds.minDeliveryDays, bounds.maxDeliveryDays],
        contractMonths: [bounds.minContractMonths, bounds.maxContractMonths],
      })
        .filter(([key, [minimum, maximum]]) => {
          if (terms[key] === undefined) {
            return false;
          }

          return (
            Number(terms[key]) < Number(minimum) ||
            Number(terms[key]) > Number(maximum)
          );
        })
        .map(([key]) => fieldDefinitions[key].label)
    : [];

  return {
    complete: missingFields.length === 0,
    detectedFields: Object.keys(detectedTerms),
    optionReference: optionIndex === null ? null : optionIndex + 1,
    inheritedFields: baseTerms
      ? Object.keys(fieldDefinitions).filter(
          (key) => detectedTerms[key] === undefined && terms[key] !== undefined,
        )
      : [],
    missingFields,
    outOfBounds,
    terms,
  };
}

export function shouldUseAiParsing(message, parsedDraft) {
  const normalizedMessage = message.trim();

  if (!normalizedMessage) {
    return false;
  }

  if (!parsedDraft.complete) {
    return true;
  }

  return /\boption\s+\d+\b|\b(same|keep)\b/i.test(normalizedMessage);
}

function detectOptionReference(message, counterOffers) {
  if (!message || counterOffers.length === 0) {
    return null;
  }

  const match = message.match(/\boption\s+(\d+)\b/i);

  if (!match) {
    return null;
  }

  const optionIndex = Number(match[1]) - 1;

  if (optionIndex < 0 || optionIndex >= counterOffers.length) {
    return null;
  }

  return optionIndex;
}
