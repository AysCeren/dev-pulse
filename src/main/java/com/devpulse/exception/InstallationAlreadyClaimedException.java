package com.devpulse.exception;

public class InstallationAlreadyClaimedException extends RuntimeException {

	public InstallationAlreadyClaimedException() {
		super("This installation is already linked to another account");
	}

}
