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

const optionOrdinals = {
  one: 1,
  first: 1,
  "1st": 1,
  two: 2,
  second: 2,
  "2nd": 2,
  three: 3,
  third: 3,
  "3rd": 3,
};

const agreementPattern = /\b(agree|accept|accepted|works|deal|okay|ok)\b/i;

const constraintPatterns = {
  priceFloor: [
    /(?:cannot|can't|can\s+not|won't|will\s+not)\D{0,24}(?:go|be|offer|propose)?\D{0,12}(?:lower|below|under|less)\D{0,8}(?:than)?\D{0,4}(?:€|eur|euro|euros|\$|usd)?\s*(\d+(?:\.\d+)?)/i,
    /(?:minimum|min)\D{0,12}(?:price)?\D{0,12}(?:is)?\D{0,4}(?:€|eur|euro|euros|\$|usd)?\s*(\d+(?:\.\d+)?)/i,
  ],
  paymentDaysCeiling: [
    /(?:cannot|can't|can\s+not|won't|will\s+not)\D{0,24}(?:payment|pay(?:ment)?\s+terms?)\D{0,12}(?:be\s+)?(?:longer|more|above|over)\D{0,8}(?:than)?\D{0,4}(\d+)\s*days?/i,
    /(?:payment|pay(?:ment)?\s+terms?)\D{0,12}(?:cannot|can't|can\s+not|won't|will\s+not)\D{0,12}(?:be\s+)?(?:longer|more|above|over)\D{0,8}(?:than)?\D{0,4}(\d+)\s*days?/i,
    /(?:payment|pay(?:ment)?\s+terms?)\D{0,12}(?:max(?:imum)?|cap)\D{0,8}(\d+)\s*days?/i,
  ],
  deliveryDaysFloor: [
    /(?:cannot|can't|can\s+not|won't|will\s+not)\D{0,24}(?:deliver|delivery)\D{0,12}(?:before|earlier\s+than|faster\s+than|under|less\s+than)\D{0,4}(\d+)\s*days?/i,
    /(?:deliver|delivery)\D{0,12}(?:cannot|can't|can\s+not|won't|will\s+not)\D{0,12}(?:be\s+)?(?:before|earlier\s+than|faster\s+than|under|less\s+than)\D{0,4}(\d+)\s*days?/i,
    /(?:minimum|min)\D{0,12}(?:delivery)\D{0,12}(?:is)?\D{0,4}(\d+)\s*days?/i,
  ],
  contractMonthsFloor: [
    /(?:cannot|can't|can\s+not|won't|will\s+not)\D{0,24}(?:contract|term|commitment)\D{0,12}(?:be\s+)?(?:shorter|less|under)\D{0,8}(?:than)?\D{0,4}(\d+)\s*months?/i,
    /(?:contract|term|commitment)\D{0,12}(?:cannot|can't|can\s+not|won't|will\s+not)\D{0,12}(?:be\s+)?(?:shorter|less|under)\D{0,8}(?:than)?\D{0,4}(\d+)\s*months?/i,
    /(?:minimum|min)\D{0,12}(?:contract|term|commitment)\D{0,12}(?:is)?\D{0,4}(\d+)\s*months?/i,
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
  const constraints = extractSupplierConstraints(normalizedMessage);

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
    constraints,
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

function extractSupplierConstraints(message) {
  if (!message) {
    return null;
  }

  const extractedConstraints = {};

  for (const [key, patterns] of Object.entries(constraintPatterns)) {
    for (const pattern of patterns) {
      const match = message.match(pattern);

      if (match) {
        extractedConstraints[key] =
          key === "priceFloor"
            ? Number(match[1])
            : Math.round(Number(match[1]));
        break;
      }
    }
  }

  return Object.keys(extractedConstraints).length > 0
    ? extractedConstraints
    : null;
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

  const directMatch = message.match(
    /\boption\s+(\d+|one|two|three|1st|2nd|3rd|first|second|third)\b/i,
  );

  if (directMatch) {
    return normalizeOptionIndex(directMatch[1], counterOffers.length);
  }

  const reverseMatch = message.match(
    /\b(\d+|one|two|three|1st|2nd|3rd|first|second|third)\s+option\b/i,
  );

  if (reverseMatch) {
    return normalizeOptionIndex(reverseMatch[1], counterOffers.length);
  }

  if (counterOffers.length === 1 && agreementPattern.test(message)) {
    return 0;
  }

  return null;
}

function normalizeOptionIndex(rawValue, optionCount) {
  if (!rawValue) {
    return null;
  }

  const normalizedValue = rawValue.trim().toLowerCase();
  const numericValue = Number(normalizedValue);
  const optionNumber = Number.isNaN(numericValue)
    ? optionOrdinals[normalizedValue]
    : numericValue;

  if (!optionNumber) {
    return null;
  }

  const optionIndex = optionNumber - 1;

  if (optionIndex < 0 || optionIndex >= optionCount) {
    return null;
  }

  return optionIndex;
}
