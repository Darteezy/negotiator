package org.GLM.negoriator.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AdminApiGuard {

	public static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";
	private final String configuredToken;

	public AdminApiGuard(@Value("${negotiator.admin-token:}") String configuredToken) {
		this.configuredToken = configuredToken == null ? "" : configuredToken.trim();
	}

	public void assertAuthorized(String providedToken) {
		if (configuredToken.isBlank()) {
			throw new AdminApiDisabledException("AI and simulation endpoints are disabled until negotiator.admin-token is configured.");
		}

		if (providedToken == null || providedToken.isBlank()) {
			throw new ApiAccessDeniedException("Admin API token is required.");
		}

		if (!MessageDigest.isEqual(
			configuredToken.getBytes(StandardCharsets.UTF_8),
			providedToken.trim().getBytes(StandardCharsets.UTF_8))) {
			throw new ApiAccessDeniedException("Admin API token is invalid.");
		}
	}
}
