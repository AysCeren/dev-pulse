package com.devpulse.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Response of GitHub's GET /app/installations/{id}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubAppInstallationDto(
	Long id,
	GitHubAccountDto account,
	@JsonProperty("repository_selection") String repositorySelection,
	@JsonProperty("created_at") Instant createdAt
) {
}
