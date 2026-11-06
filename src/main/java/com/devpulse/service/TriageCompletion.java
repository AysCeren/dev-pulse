package com.devpulse.service;

import com.devpulse.domain.entity.IssueSeverity;
import com.devpulse.domain.entity.IssueType;

import java.util.List;

public record TriageCompletion(
	IssueSeverity severity,
	IssueType issueType,
	String summary,
	List<String> suggestedLabels,
	String rawResponse
) {
}
