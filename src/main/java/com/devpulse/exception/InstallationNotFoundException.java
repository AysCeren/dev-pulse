package com.devpulse.exception;

public class InstallationNotFoundException extends RuntimeException {

	public InstallationNotFoundException() {
		super("GitHub has no such installation for this app");
	}

}
