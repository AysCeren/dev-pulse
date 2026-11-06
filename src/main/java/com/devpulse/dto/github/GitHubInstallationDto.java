package com.devpulse.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubInstallationDto(
	Long id
) {
}
