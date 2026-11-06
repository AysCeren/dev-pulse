package com.devpulse.exception;

public class TriageFailedException extends RuntimeException {

	public TriageFailedException(String message) {
		super(message);
	}

	public TriageFailedException(String message, Throwable cause) {
		super(message, cause);
	}

}
