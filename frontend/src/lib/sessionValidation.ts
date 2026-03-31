import type { ApiBounds, OfferTerms } from "@/lib/types";

interface SessionValidationInput {
  maxRounds: number;
  riskOfWalkaway: number;
  idealOffer: OfferTerms;
  reservationOffer: OfferTerms;
  bounds: ApiBounds;
}

export function validateSessionInputs({
  maxRounds,
  riskOfWalkaway,
  idealOffer,
  reservationOffer,
  bounds,
}: SessionValidationInput) {
  if (!Number.isInteger(maxRounds) || maxRounds <= 0) {
    return "Max rounds must be greater than zero.";
  }

  if (riskOfWalkaway < 0 || riskOfWalkaway > 1) {
    return "Walkaway risk must stay between 0 and 1.";
  }

  const nonNegativeValues = [
    idealOffer.price,
    idealOffer.paymentDays,
    idealOffer.deliveryDays,
    idealOffer.contractMonths,
    reservationOffer.price,
    reservationOffer.paymentDays,
    reservationOffer.deliveryDays,
    reservationOffer.contractMonths,
    bounds.minPrice,
    bounds.maxPrice,
    bounds.minPaymentDays,
    bounds.maxPaymentDays,
    bounds.minDeliveryDays,
    bounds.maxDeliveryDays,
    bounds.minContractMonths,
    bounds.maxContractMonths,
  ];

  if (nonNegativeValues.some((value) => value < 0)) {
    return "Values must be zero or greater.";
  }

  if (bounds.minPrice > bounds.maxPrice) {
    return "Price bounds must have a minimum below or equal to the maximum.";
  }
  if (bounds.minPaymentDays > bounds.maxPaymentDays) {
    return "Payment bounds must have a minimum below or equal to the maximum.";
  }
  if (bounds.minDeliveryDays > bounds.maxDeliveryDays) {
    return "Delivery bounds must have a minimum below or equal to the maximum.";
  }
  if (bounds.minContractMonths > bounds.maxContractMonths) {
    return "Contract bounds must have a minimum below or equal to the maximum.";
  }

  if (idealOffer.price > reservationOffer.price) {
    return "Preferred price must be below or equal to the walk-away ceiling.";
  }
  if (idealOffer.paymentDays < reservationOffer.paymentDays) {
    return "Preferred payment days must be greater than or equal to the minimum acceptable payment days.";
  }
  if (idealOffer.deliveryDays > reservationOffer.deliveryDays) {
    return "Preferred delivery must be faster than or equal to the maximum acceptable delivery.";
  }
  if (idealOffer.contractMonths > reservationOffer.contractMonths) {
    return "Preferred contract length must be shorter than or equal to the maximum acceptable contract length.";
  }

  return null;
}

export function normalizeSessionBounds(
  bounds: ApiBounds,
  idealOffer: OfferTerms,
  reservationOffer: OfferTerms,
): ApiBounds {
  return {
    minPrice: Math.min(
      bounds.minPrice,
      idealOffer.price,
      reservationOffer.price,
    ),
    maxPrice: Math.max(
      bounds.maxPrice,
      idealOffer.price,
      reservationOffer.price,
    ),
    minPaymentDays: Math.min(
      bounds.minPaymentDays,
      idealOffer.paymentDays,
      reservationOffer.paymentDays,
    ),
    maxPaymentDays: Math.max(
      bounds.maxPaymentDays,
      idealOffer.paymentDays,
      reservationOffer.paymentDays,
    ),
    minDeliveryDays: Math.min(
      bounds.minDeliveryDays,
      idealOffer.deliveryDays,
      reservationOffer.deliveryDays,
    ),
    maxDeliveryDays: Math.max(
      bounds.maxDeliveryDays,
      idealOffer.deliveryDays,
      reservationOffer.deliveryDays,
    ),
    minContractMonths: Math.min(
      bounds.minContractMonths,
      idealOffer.contractMonths,
      reservationOffer.contractMonths,
    ),
    maxContractMonths: Math.max(
      bounds.maxContractMonths,
      idealOffer.contractMonths,
      reservationOffer.contractMonths,
    ),
  };
}
