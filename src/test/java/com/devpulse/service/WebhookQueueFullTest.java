package com.devpulse.service;

import com.devpulse.dto.github.GitHubInstallationDto;
import com.devpulse.dto.github.GitHubIssueDto;
import com.devpulse.dto.github.GitHubRepositoryDto;
import com.devpulse.dto.github.GitHubSenderDto;
import com.devpulse.dto.github.GitHubWebhookPayload;
import com.devpulse.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

class WebhookQueueFullTest extends IntegrationTestBase {

	@MockitoBean
	private TriageWorker triageWorker;

	@Autowired
	private WebhookProcessingOrchestrator orchestrator;

	@Test
	void fullQueueLeavesIssueStoredAsNew() {
		insertInstallation(99L);
		doThrow(new TaskRejectedException("full")).when(triageWorker).triage(any(UUID.class));
		GitHubSenderDto user = new GitHubSenderDto(1L, "octocat", null);
		GitHubWebhookPayload payload = new GitHubWebhookPayload(
			"opened",
			new GitHubIssueDto(5L, 1, "Title", null, "open", "https://github.com/o/r/issues/1", user),
			new GitHubRepositoryDto(1L, "r", "o/r", "https://github.com/o/r"),
			user,
			new GitHubInstallationDto(99L)
		);

		orchestrator.handleIssueOpened(payload, "delivery-1");

		assertThat(jdbcTemplate.queryForObject("SELECT status FROM issues", String.class)).isEqualTo("NEW");
	}

}
