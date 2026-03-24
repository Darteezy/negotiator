import type { BuyerPreferences, OfferTerms } from "./types";

export interface ParseResult {
  extracted: Partial<BuyerPreferences>;
  missing: string[];
  complete: boolean;
}

export interface TermBounds {
  minPrice: number;
  maxPrice: number;
  minPaymentDays: number;
  maxPaymentDays: number;
  minDeliveryDays: number;
  maxDeliveryDays: number;
  minContractMonths: number;
  maxContractMonths: number;
}

/**
 * Parse natural language input to extract buyer configuration terms.
 * Supports various phrasings and formats.
 */
export function parseConfigurationMessage(
  text: string,
  bounds?: TermBounds
): ParseResult {
  const lower = text.toLowerCase();
  const extracted: Partial<BuyerPreferences> = {};

  // Extract price (preferred and max)
  const priceResult = extractPrice(lower);
  if (priceResult) {
    extracted.price = priceResult;
  }

  // Extract payment days (preferred and max)
  const paymentResult = extractPaymentDays(lower);
  if (paymentResult) {
    extracted.paymentDays = paymentResult;
  }

  // Extract delivery days (preferred and max)
  const deliveryResult = extractDeliveryDays(lower);
  if (deliveryResult) {
    extracted.deliveryDays = deliveryResult;
  }

  // Extract contract months (preferred and range)
  const contractResult = extractContractMonths(lower);
  if (contractResult) {
    extracted.contractMonths = contractResult;
  }

  // Determine missing fields
  const missing = [];
  if (!extracted.price) {
    missing.push("price (preferred and max)");
  }
  if (!extracted.paymentDays) {
    missing.push("payment terms");
  }
  if (!extracted.deliveryDays) {
    missing.push("delivery time");
  }
  if (!extracted.contractMonths) {
    missing.push("contract length");
  }

  return {
    extracted,
    missing,
    complete: missing.length === 0,
  };
}

/**
 * Refine existing preferences by parsing additional user input.
 * Merges new extractions with previous partial data.
 */
export function refinePreferences(
  current: Partial<BuyerPreferences>,
  userInput: string
): ParseResult {
  const parsed = parseConfigurationMessage(userInput);

  // Merge: new extractions override old ones
  const merged: Partial<BuyerPreferences> = {
    ...current,
    ...parsed.extracted,
  };

  // Recalculate missing fields
  const missing = [];
  if (!merged.price) missing.push("price");
  if (!merged.paymentDays) missing.push("payment days");
  if (!merged.deliveryDays) missing.push("delivery days");
  if (!merged.contractMonths) missing.push("contract months");

  return {
    extracted: merged,
    missing,
    complete: missing.length === 0,
  };
}

/**
 * Create a complete BuyerPreferences object from partial extraction.
 * Uses defaults for any missing values.
 */
export function createFullPreferences(
  extracted: Partial<BuyerPreferences>,
  defaults: BuyerPreferences
): BuyerPreferences {
  return {
    price: extracted.price ?? defaults.price,
    paymentDays: extracted.paymentDays ?? defaults.paymentDays,
    deliveryDays: extracted.deliveryDays ?? defaults.deliveryDays,
    contractMonths: extracted.contractMonths ?? defaults.contractMonths,
    weights: extracted.weights ?? defaults.weights,
    thresholds: extracted.thresholds ?? defaults.thresholds,
    maxRounds: extracted.maxRounds ?? defaults.maxRounds,
  };
}

// ============================================================================
// PARSING HELPERS
// ============================================================================

/**
 * Extract price (both preferred and max if available).
 * Handles formats: "€10k", "10000", "budget 10k max 12k", etc.
 */
function extractPrice(text: string): { preferred: number; max: number } | null {
  // Pattern: look for currency symbols or "price", "budget", "cost"
  // Try to find one or two prices

  // Remove common separators and normalize
  const cleaned = text
    .replace(/€/g, "")
    .replace(/,/g, "")
    .replace(/\./g, "");

  // Match patterns like "budget X max Y" or "price X"
  const budgetPatterns = [
    /(?:budget|cost|price|spending)(?:\s+is)?[\s:]{0,3}([0-9]+k?)[^\d]*max[\s:]{0,3}([0-9]+k?)/i,
    /([0-9]+k?)[\s]*(?:to|[-–])?[\s]*([0-9]+k?)\s*(?:euro|eur|\€|price|budget)/i,
    /(?:budget|price|cost)[\s:]{0,3}([0-9]+k?)[^\d]*(?:max|limit|up to)[\s:]{0,3}([0-9]+k?)/i,
  ];

  for (const pattern of budgetPatterns) {
    const match = pattern.exec(text);
    if (match) {
      const preferred = parseNumber(match[1]);
      const max = parseNumber(match[2]);
      if (preferred && max) {
        return { preferred: Math.min(preferred, max), max: Math.max(preferred, max) };
      }
    }
  }

  // Fallback: single price value - use as both preferred and max
  const singleNumber = /(?:budget|price|cost|spending|target)[\s:]{0,3}([0-9]+k?)/i;
  const singleMatch = singleNumber.exec(text);
  if (singleMatch) {
    const val = parseNumber(singleMatch[1]);
    if (val) {
      return { preferred: val, max: val };
    }
  }

  return null;
}

/**
 * Extract payment days (both preferred and max).
 * Handles: "30 days", "30-60 days", "payment 30d max 60d", etc.
 */
function extractPaymentDays(text: string): { preferred: number; max: number } | null {
  const patterns = [
    /payment[\s:]{0,3}([0-9]+)[\s]*(?:to|[-–])?[\s]*(?:days)?[\s]*(?:max|up to)?[\s]*([0-9]+)\s*(?:days?|d)/i,
    /([0-9]+)\s*[-–]\s*([0-9]+)\s*days?\s*(?:payment|pay)/i,
    /payment[\s:]{0,3}([0-9]+)\s*days?[\s]*(?:max|limit|up to)[\s:]{0,3}([0-9]+)/i,
    /(?:payment|pay|terms?)[\s:]{0,3}([0-9]+)\s*(?:to|[-–])\s*([0-9]+)/i,
  ];

  for (const pattern of patterns) {
    const match = pattern.exec(text);
    if (match) {
      const first = parseInt(match[1], 10);
      const second = parseInt(match[2], 10);
      if (!isNaN(first) && !isNaN(second)) {
        return { preferred: Math.min(first, second), max: Math.max(first, second) };
      }
    }
  }

  // Single value: "30 days payment" or "pay in 30 days"
  const single = /(?:payment|pay)[\s:]{0,3}(?:in\s+)?([0-9]+)\s*(?:days?|d)/i;
  const singleMatch = single.exec(text);
  if (singleMatch) {
    const val = parseInt(singleMatch[1], 10);
    if (!isNaN(val)) {
      return { preferred: val, max: val };
    }
  }

  return null;
}

/**
 * Extract delivery days (both preferred and max).
 * Handles: "7 days", "7-14 days", "delivery 10 days max 20 days", etc.
 */
function extractDeliveryDays(text: string): { preferred: number; max: number } | null {
  const patterns = [
    /delivery[\s:]{0,3}([0-9]+)[\s]*(?:to|[-–])?[\s]*(?:days)?[\s]*(?:max|up to)?[\s]*([0-9]+)\s*(?:days?|d)/i,
    /([0-9]+)\s*[-–]\s*([0-9]+)\s*days?\s*(?:delivery|deliver|lead)/i,
    /delivery[\s:]{0,3}([0-9]+)\s*(?:days?|d)[\s]*(?:max|limit|up to)[\s:]{0,3}([0-9]+)/i,
    /(?:delivery|deliver|lead\s*time)[\s:]{0,3}([0-9]+)\s*(?:to|[-–])\s*([0-9]+)/i,
  ];

  for (const pattern of patterns) {
    const match = pattern.exec(text);
    if (match) {
      const first = parseInt(match[1], 10);
      const second = parseInt(match[2], 10);
      if (!isNaN(first) && !isNaN(second)) {
        return { preferred: Math.min(first, second), max: Math.max(first, second) };
      }
    }
  }

  // Single value: "10 days delivery" or "deliver in 10 days"
  const single = /(?:delivery|deliver|lead\s*time)[\s:]{0,3}(?:in\s+)?([0-9]+)\s*(?:days?|d)/i;
  const singleMatch = single.exec(text);
  if (singleMatch) {
    const val = parseInt(singleMatch[1], 10);
    if (!isNaN(val)) {
      return { preferred: val, max: val };
    }
  }

  return null;
}

/**
 * Extract contract months (preferred and range).
 * Handles: "12 months", "6-18 months", "contract 12m", etc.
 */
function extractContractMonths(text: string): { preferred: number; min: number; max: number } | null {
  const patterns = [
    /contract[\s:]{0,3}([0-9]+)\s*[-–]\s*([0-9]+)\s*months?/i,
    /([0-9]+)\s*[-–]\s*([0-9]+)\s*months?\s*(?:contract|term|commitment)/i,
    /contract[\s:]{0,3}(?:range\s+)?([0-9]+)\s*(?:to|[-–])\s*([0-9]+)\s*months?/i,
  ];

  for (const pattern of patterns) {
    const match = pattern.exec(text);
    if (match) {
      const first = parseInt(match[1], 10);
      const second = parseInt(match[2], 10);
      if (!isNaN(first) && !isNaN(second)) {
        const min = Math.min(first, second);
        const max = Math.max(first, second);
        // Use middle as preferred
        const preferred = Math.round((min + max) / 2);
        return { preferred, min, max };
      }
    }
  }

  // Single value: "12 month contract"
  const single = /(?:contract|commitment)[\s:]{0,3}([0-9]+)\s*months?/i;
  const singleMatch = single.exec(text);
  if (singleMatch) {
    const val = parseInt(singleMatch[1], 10);
    if (!isNaN(val)) {
      return { preferred: val, min: val, max: val };
    }
  }

  return null;
}

/**
 * Parse a number that might be formatted as "10k", "10000", "10.5", etc.
 */
function parseNumber(input: string): number | null {
  if (!input) return null;

  const lower = input.toLowerCase().trim();

  // Handle "k" suffix (thousands)
  if (lower.endsWith("k")) {
    const base = parseFloat(lower.slice(0, -1));
    return isNaN(base) ? null : base * 1000;
  }

  // Regular number
  const num = parseFloat(lower);
  return isNaN(num) ? null : num;
}

/**
 * Extract live preview of detected values from real-time input.
 * Runs parseConfigurationMessage but also returns human-readable extracted values.
 */
export function extractPreviewTerms(text: string): {
  price?: string;
  paymentDays?: string;
  deliveryDays?: string;
  contractMonths?: string;
} {
  const result = parseConfigurationMessage(text);
  const preview: Record<string, string | undefined> = {};

  if (result.extracted.price) {
    const p = result.extracted.price;
    if (p.preferred === p.max) {
      preview.price = `€${p.preferred}`;
    } else {
      preview.price = `€${p.preferred} - €${p.max}`;
    }
  }

  if (result.extracted.paymentDays) {
    const pd = result.extracted.paymentDays;
    if (pd.preferred === pd.max) {
      preview.paymentDays = `${pd.preferred}d`;
    } else {
      preview.paymentDays = `${pd.preferred}-${pd.max}d`;
    }
  }

  if (result.extracted.deliveryDays) {
    const dd = result.extracted.deliveryDays;
    if (dd.preferred === dd.max) {
      preview.deliveryDays = `${dd.preferred}d`;
    } else {
      preview.deliveryDays = `${dd.preferred}-${dd.max}d`;
    }
  }

  if (result.extracted.contractMonths) {
    const cm = result.extracted.contractMonths;
    if (cm.min === cm.max) {
      preview.contractMonths = `${cm.preferred}m`;
    } else {
      preview.contractMonths = `${cm.min}-${cm.max}m`;
    }
  }

  return preview;
}
