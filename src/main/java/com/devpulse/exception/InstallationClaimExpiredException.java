package com.devpulse.exception;

public class InstallationClaimExpiredException extends RuntimeException {

	public InstallationClaimExpiredException() {
		super("This installation is too old to be linked. Reinstall the app and try again");
	}

}
