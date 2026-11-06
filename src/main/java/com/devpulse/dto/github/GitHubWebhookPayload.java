package com.devpulse.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubWebhookPayload(
	String action,
	GitHubIssueDto issue,
	GitHubRepositoryDto repository,
	GitHubSenderDto sender,
	GitHubInstallationDto installation
) {
}
