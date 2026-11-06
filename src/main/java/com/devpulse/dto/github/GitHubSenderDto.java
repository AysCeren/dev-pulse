package com.devpulse.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubSenderDto(
	Long id,
	String login,
	@JsonProperty("avatar_url") String avatarUrl
) {
}
