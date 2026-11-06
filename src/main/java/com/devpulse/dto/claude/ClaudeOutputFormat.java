package com.devpulse.dto.claude;

import java.util.Map;

public record ClaudeOutputFormat(
	String type,
	Map<String, Object> schema
) {
}
