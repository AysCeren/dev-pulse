package com.devpulse.exception;

public class GitHubApiException extends RuntimeException {

	public GitHubApiException() {
		super("GitHub could not be reached. Try again");
	}

}
