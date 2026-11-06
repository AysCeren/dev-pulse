package com.devpulse.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

	public EmailAlreadyRegisteredException() {
		super("This email is already registered");
	}

}
