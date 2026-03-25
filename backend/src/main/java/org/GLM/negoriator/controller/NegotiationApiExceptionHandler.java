package org.GLM.negoriator.controller;

import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class NegotiationApiExceptionHandler {

	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<ApiError> handleEntityNotFound(EntityNotFoundException exception) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(new ApiError(exception.getMessage()));
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ApiError> handleIllegalState(IllegalStateException exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(new ApiError(exception.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(new ApiError(exception.getMessage()));
	}

	@ExceptionHandler({
		CannotAcquireLockException.class,
		ObjectOptimisticLockingFailureException.class,
		OptimisticLockException.class,
		PessimisticLockingFailureException.class
	})
	public ResponseEntity<ApiError> handleConcurrentUpdate(Exception exception) {
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(new ApiError("The session was updated concurrently. Retry the request."));
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException exception) {
		return ResponseEntity.status(exception.getStatusCode())
			.body(new ApiError(exception.getReason() == null ? exception.getMessage() : exception.getReason()));
	}

	public record ApiError(String message) {
	}
}
