package com.devpulse.dto.claude;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IssueTriageResult(
	String severity,
	@JsonProperty("issue_type") String issueType,
	String summary,
	@JsonProperty("suggested_labels") List<String> suggestedLabels
) {
}
