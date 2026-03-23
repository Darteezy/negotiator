package org.GLM.negoriator.negotiation;

import java.math.BigDecimal;

import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;

final class BuyerPreferenceScoring {

	private BuyerPreferenceScoring() {
	}

	static BigDecimal priceScore(OfferVector offer, BuyerProfile profile) {
		return Normalization.normalizeInvertedDecimal(
			offer.price(),
			profile.idealOffer().price(),
			profile.reservationOffer().price());
	}

	static BigDecimal paymentScore(OfferVector offer, BuyerProfile profile) {
		return Normalization.normalizePositiveInt(
			offer.paymentDays(),
			profile.reservationOffer().paymentDays(),
			profile.idealOffer().paymentDays());
	}

	static BigDecimal deliveryScore(OfferVector offer, BuyerProfile profile) {
		return Normalization.normalizeNegativeInt(
			offer.deliveryDays(),
			profile.idealOffer().deliveryDays(),
			profile.reservationOffer().deliveryDays());
	}

	static BigDecimal contractScore(OfferVector offer, BuyerProfile profile) {
		return Normalization.normalizeNegativeInt(
			offer.contractMonths(),
			profile.idealOffer().contractMonths(),
			profile.reservationOffer().contractMonths());
	}

	static BigDecimal priceSpan(BuyerProfile profile) {
		return profile.reservationOffer().price().subtract(profile.idealOffer().price());
	}

	static BigDecimal paymentSpan(BuyerProfile profile) {
		return BigDecimal.valueOf(profile.idealOffer().paymentDays() - profile.reservationOffer().paymentDays());
	}

	static BigDecimal deliverySpan(BuyerProfile profile) {
		return BigDecimal.valueOf(profile.reservationOffer().deliveryDays() - profile.idealOffer().deliveryDays());
	}

	static BigDecimal contractSpan(BuyerProfile profile) {
		return BigDecimal.valueOf(profile.reservationOffer().contractMonths() - profile.idealOffer().contractMonths());
	}
}
