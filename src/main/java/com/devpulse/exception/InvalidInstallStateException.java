package com.devpulse.exception;

public class InvalidInstallStateException extends RuntimeException {

	public InvalidInstallStateException() {
		super("The installation link is invalid or has expired");
	}

}
