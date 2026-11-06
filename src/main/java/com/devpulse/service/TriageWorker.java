package com.devpulse.service;

import com.devpulse.exception.TriageFailedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Deliberately not transactional: holding a database connection during the Claude call would
 * exhaust the connection pool under load.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriageWorker {

	private final TriageRecordService recordService;
	private final ClaudeTriageService triageService;

	@Async("triageTaskExecutor")
	public void triage(UUID issueId) {
		recordService.findPendingIssue(issueId).ifPresent(issue -> {
			try {
				TriageCompletion completion = triageService.triageIssue(issue.title(), issue.description());
				recordService.recordSuccess(issueId, completion);
				log.info("Triaged issue {}", issueId);
			}
			catch (TriageFailedException ex) {
				log.warn("Triage failed for issue {}: {}", issueId, ex.getMessage());
				recordService.recordFailure(issueId);
			}
		});
	}

}
