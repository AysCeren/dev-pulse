package com.devpulse.service;

import com.devpulse.domain.entity.Issue;
import com.devpulse.domain.entity.IssueStatus;
import com.devpulse.domain.entity.TriageMetadata;
import com.devpulse.repository.IssueRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * The short database transactions on either side of the Claude call.
 */
@Service
@RequiredArgsConstructor
public class TriageRecordService {

	private final IssueRepository issueRepository;

	@Transactional(readOnly = true)
	public Optional<IssueSnapshot> findPendingIssue(UUID issueId) {
		return issueRepository.findById(issueId)
			.filter(issue -> issue.getStatus() == IssueStatus.NEW)
			.map(issue -> new IssueSnapshot(issue.getTitle(), issue.getDescription()));
	}

	@Transactional
	public void recordSuccess(UUID issueId, TriageCompletion completion) {
		Issue issue = issueRepository.findWithTriageById(issueId).orElseThrow();

		TriageMetadata metadata = new TriageMetadata();
		metadata.setSeverity(completion.severity());
		metadata.setIssueType(completion.issueType());
		metadata.setSummary(completion.summary());
		metadata.setSuggestedLabels(completion.suggestedLabels());
		metadata.setRawAiResponse(completion.rawResponse());

		issue.setTriageMetadata(metadata);
		issue.setStatus(IssueStatus.TRIAGED);
	}

	@Transactional
	public void recordFailure(UUID issueId) {
		issueRepository.findById(issueId).orElseThrow().setStatus(IssueStatus.TRIAGE_FAILED);
	}

}
