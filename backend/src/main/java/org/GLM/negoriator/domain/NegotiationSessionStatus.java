package org.GLM.negoriator.domain;

import org.GLM.negoriator.negotiation.NegotiationEngine.NegotiationState;

public enum NegotiationSessionStatus {

	PENDING,
	COUNTERED,
	ACCEPTED,
	REJECTED,
	EXPIRED;

	public NegotiationState toNegotiationState() {
		return NegotiationState.valueOf(name());
	}
}
