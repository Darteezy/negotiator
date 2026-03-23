package org.GLM.negoriator.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;

import org.GLM.negoriator.negotiation.NegotiationEngine.BuyerProfile;
import org.GLM.negoriator.negotiation.NegotiationEngine.OfferVector;
import org.junit.jupiter.api.Test;

class SessionConfigurationValidatorTest {

	private final SessionConfigurationValidator validator = new SessionConfigurationValidator();

	@Test
	void acceptsDefaultSessionConfiguration() {
		assertDoesNotThrow(() -> validator.validate(NegotiationDefaults.startSessionCommand()));
	}

	@Test
	void rejectsPriceGoalAboveReservationLimit() {
		BuyerProfile invalidBuyerProfile = new BuyerProfile(
			new OfferVector(new BigDecimal("121.00"), 60, 7, 6),
			new OfferVector(new BigDecimal("120.00"), 30, 30, 24),
			NegotiationDefaults.buyerProfile().weights(),
			NegotiationDefaults.buyerProfile().reservationUtility());

		var command = new NegotiationApplicationService.StartSessionCommand(
			NegotiationDefaults.defaultStrategy(),
			NegotiationDefaults.maxRounds(),
			NegotiationDefaults.riskOfWalkaway(),
			invalidBuyerProfile,
			NegotiationDefaults.bounds(),
			NegotiationDefaults.supplierModel());

		assertThrows(IllegalArgumentException.class, () -> validator.validate(command));
	}
}
