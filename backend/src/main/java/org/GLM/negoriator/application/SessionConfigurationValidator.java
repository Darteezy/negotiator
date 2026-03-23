package org.GLM.negoriator.application;

import java.math.BigDecimal;

import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationBounds;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;

final class SessionConfigurationValidator {

	void validate(NegotiationApplicationService.StartSessionCommand command) {
		if (command.maxRounds() <= 0) {
			throw new IllegalArgumentException("maxRounds must be greater than zero.");
		}

		if (command.riskOfWalkaway().compareTo(BigDecimal.ZERO) < 0
			|| command.riskOfWalkaway().compareTo(BigDecimal.ONE) > 0) {
			throw new IllegalArgumentException("riskOfWalkaway must be between 0 and 1.");
		}

		validateBuyerProfile(command.buyerProfile(), command.bounds());
	}

	private void validateBuyerProfile(BuyerProfile buyerProfile, NegotiationBounds bounds) {
		OfferVector idealOffer = buyerProfile.idealOffer();
		OfferVector reservationOffer = buyerProfile.reservationOffer();

		validateOfferWithinBounds("buyerProfile.idealOffer", idealOffer, bounds);
		validateOfferWithinBounds("buyerProfile.reservationOffer", reservationOffer, bounds);

		if (idealOffer.price().compareTo(reservationOffer.price()) > 0) {
			throw new IllegalArgumentException("buyerProfile.idealOffer.price must be less than or equal to buyerProfile.reservationOffer.price.");
		}
		if (idealOffer.paymentDays() < reservationOffer.paymentDays()) {
			throw new IllegalArgumentException("buyerProfile.idealOffer.paymentDays must be greater than or equal to buyerProfile.reservationOffer.paymentDays.");
		}
		if (idealOffer.deliveryDays() > reservationOffer.deliveryDays()) {
			throw new IllegalArgumentException("buyerProfile.idealOffer.deliveryDays must be less than or equal to buyerProfile.reservationOffer.deliveryDays.");
		}
		if (idealOffer.contractMonths() > reservationOffer.contractMonths()) {
			throw new IllegalArgumentException("buyerProfile.idealOffer.contractMonths must be less than or equal to buyerProfile.reservationOffer.contractMonths.");
		}
		if (buyerProfile.reservationUtility().compareTo(BigDecimal.ZERO) < 0
			|| buyerProfile.reservationUtility().compareTo(BigDecimal.ONE) > 0) {
			throw new IllegalArgumentException("buyerProfile.reservationUtility must be between 0 and 1.");
		}
	}

	private void validateOfferWithinBounds(String label, OfferVector offer, NegotiationBounds bounds) {
		if (offer.price().compareTo(bounds.minPrice()) < 0 || offer.price().compareTo(bounds.maxPrice()) > 0) {
			throw new IllegalArgumentException(label + ".price must stay within negotiation bounds.");
		}
		if (offer.paymentDays() < bounds.minPaymentDays() || offer.paymentDays() > bounds.maxPaymentDays()) {
			throw new IllegalArgumentException(label + ".paymentDays must stay within negotiation bounds.");
		}
		if (offer.deliveryDays() < bounds.minDeliveryDays() || offer.deliveryDays() > bounds.maxDeliveryDays()) {
			throw new IllegalArgumentException(label + ".deliveryDays must stay within negotiation bounds.");
		}
		if (offer.contractMonths() < bounds.minContractMonths() || offer.contractMonths() > bounds.maxContractMonths()) {
			throw new IllegalArgumentException(label + ".contractMonths must stay within negotiation bounds.");
		}
	}
}
