package com.devpulse.service;

import com.devpulse.dto.github.GitHubWebhookPayload;
import com.devpulse.exception.MalformedWebhookException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Not transactional, so the issue is committed before the worker can pick it up.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookProcessingOrchestrator {

	private final IssueIngestionService ingestionService;
	private final TriageWorker triageWorker;
	private final GitHubInstallationService installationService;

	public void handleIssueOpened(GitHubWebhookPayload payload, String deliveryId) {
		Optional<UUID> issueId = ingest(payload, deliveryId);
		issueId.ifPresent(id -> dispatch(id, deliveryId));
	}

	public void handleInstallationDeleted(GitHubWebhookPayload payload) {
		if (payload.installation() == null || payload.installation().id() == null) {
			throw new MalformedWebhookException("Installation event is missing its ID");
		}
		installationService.markUninstalled(payload.installation().id());
	}

	private Optional<UUID> ingest(GitHubWebhookPayload payload, String deliveryId) {
		try {
			return ingestionService.ingest(payload);
		}
		catch (DataIntegrityViolationException ex) {
			// Two deliveries of one event can both pass the existence check, and the unique constraint stops the second.
			log.info("Ignoring duplicate issue event, delivery {}", deliveryId);
			return Optional.empty();
		}
	}

	private void dispatch(UUID issueId, String deliveryId) {
		try {
			triageWorker.triage(issueId);
			log.info("Stored issue {} and dispatched triage, delivery {}", issueId, deliveryId);
		}
		catch (TaskRejectedException ex) {
			// The issue is safely stored as NEW, so the event isn't lost. See Known Gaps in the spec.
			log.warn("Triage queue is full, leaving issue {} as NEW, delivery {}", issueId, deliveryId);
		}
	}

}
