package org.GLM.negoriator.controller;

public final class ApiAccessDeniedException extends RuntimeException {

	public ApiAccessDeniedException(String message) {
		super(message);
	}
}
