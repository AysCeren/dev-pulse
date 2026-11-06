package com.devpulse.dto.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GitHubIssueDto(
	Long id,
	Integer number,
	String title,
	String body,
	String state,
	@JsonProperty("html_url") String htmlUrl,
	GitHubSenderDto user
) {
}
