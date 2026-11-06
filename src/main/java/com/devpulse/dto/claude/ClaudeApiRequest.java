package com.devpulse.dto.claude;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ClaudeApiRequest(
	String model,
	@JsonProperty("max_tokens") int maxTokens,
	String system,
	List<ClaudeMessage> messages,
	@JsonProperty("output_config") ClaudeOutputConfig outputConfig,
	String fallbacks
) {
}
