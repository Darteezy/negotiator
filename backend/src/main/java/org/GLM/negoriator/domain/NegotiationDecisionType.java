package org.GLM.negoriator.domain;

import org.GLM.negoriator.negotiation.NegotiationEngine.Decision;

public enum NegotiationDecisionType {

	ACCEPT,
	COUNTER,
	REJECT;

	public static NegotiationDecisionType from(Decision decision) {
		return NegotiationDecisionType.valueOf(decision.name());
	}

	public Decision toDecision() {
		return Decision.valueOf(name());
	}
}
