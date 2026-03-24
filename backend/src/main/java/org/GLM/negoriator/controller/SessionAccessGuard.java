package org.GLM.negoriator.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.GLM.negoriator.domain.NegotiationSession;
import org.springframework.stereotype.Component;

@Component
public class SessionAccessGuard {

	public static final String SESSION_TOKEN_HEADER = "X-Session-Token";

	public void assertAuthorized(NegotiationSession session, String providedToken) {
		String expectedToken = session.getSessionToken();

		if (expectedToken == null || expectedToken.isBlank()) {
			throw new ApiAccessDeniedException("Negotiation session token is not available for this session.");
		}

		if (providedToken == null || providedToken.isBlank()) {
			throw new ApiAccessDeniedException("Negotiation session token is required.");
		}

		if (!MessageDigest.isEqual(
			expectedToken.getBytes(StandardCharsets.UTF_8),
			providedToken.trim().getBytes(StandardCharsets.UTF_8))) {
			throw new ApiAccessDeniedException("Negotiation session token is invalid.");
		}
	}
}
