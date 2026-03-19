package org.GLM.negoriator.domain;

import org.GLM.negoriator.negotiation.NegotiationEngine.Decision;

public enum NegotiationDecisionType {

	ACCEPT,
	COUNTER,
	REJECT;

	public Decision toDecision() {
		return Decision.valueOf(name());
	}
}
