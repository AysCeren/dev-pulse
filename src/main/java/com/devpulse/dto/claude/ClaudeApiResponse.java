package com.devpulse.dto.claude;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClaudeApiResponse(
	String id,
	String type,
	String role,
	List<ContentBlock> content,
	@JsonProperty("stop_reason") String stopReason
) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ContentBlock(
		String type,
		String text
	) {
	}

}
