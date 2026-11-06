package com.devpulse.dto.claude;

public record ClaudeMessage(
	String role,
	String content
) {
}
