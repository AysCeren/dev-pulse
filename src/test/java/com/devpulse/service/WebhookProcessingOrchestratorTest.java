package com.devpulse.service;

import com.devpulse.dto.github.GitHubInstallationDto;
import com.devpulse.dto.github.GitHubIssueDto;
import com.devpulse.dto.github.GitHubRepositoryDto;
import com.devpulse.dto.github.GitHubSenderDto;
import com.devpulse.dto.github.GitHubWebhookPayload;
import com.devpulse.exception.MalformedWebhookException;
import com.devpulse.support.IntegrationTestBase;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class WebhookProcessingOrchestratorTest extends IntegrationTestBase {

	private static final long INSTALLATION = 99L;
	private static final AtomicLong ISSUE_IDS = new AtomicLong(1000);

	@Autowired
	private WebhookProcessingOrchestrator orchestrator;

	@Autowired
	private HikariDataSource dataSource;

	@Test
	void storesAndTriagesIssue() {
		insertInstallation(INSTALLATION);

		orchestrator.handleIssueOpened(payload(ISSUE_IDS.incrementAndGet()), "delivery-1");

		awaitStatus("TRIAGED");
		assertThat(count("triage_metadata")).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT severity FROM triage_metadata", String.class)).isEqualTo("HIGH");
		assertThat(jdbcTemplate.queryForObject("SELECT raw_ai_response::text FROM triage_metadata", String.class)).contains("severity");
		assertThat(jdbcTemplate.queryForObject("SELECT suggested_labels::text FROM triage_metadata", String.class)).contains("bug");
	}

	@Test
	void marksIssueFailedWithoutInventingTriage() {
		insertInstallation(INSTALLATION);
		CLAUDE.respondWith(529, "{\"type\":\"error\"}");

		orchestrator.handleIssueOpened(payload(ISSUE_IDS.incrementAndGet()), "delivery-1");

		awaitStatus("TRIAGE_FAILED");
		assertThat(count("triage_metadata")).isZero();
	}

	@Test
	void holdsNoDatabaseConnectionDuringClaudeCall() {
		insertInstallation(INSTALLATION);
		CLAUDE.delayResponsesBy(Duration.ofSeconds(2));
		int requestsBefore = CLAUDE.requestCount();

		orchestrator.handleIssueOpened(payload(ISSUE_IDS.incrementAndGet()), "delivery-1");

		// The issue is committed as NEW while the call is still in flight.
		await().atMost(Duration.ofSeconds(1)).until(() -> CLAUDE.requestCount() > requestsBefore);
		assertThat(jdbcTemplate.queryForObject("SELECT status FROM issues", String.class)).isEqualTo("NEW");
		assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();

		awaitStatus("TRIAGED");
	}

	@Test
	void redeliveryCreatesNoDuplicateAndNoSecondClaudeCall() {
		insertInstallation(INSTALLATION);
		long issueId = ISSUE_IDS.incrementAndGet();

		orchestrator.handleIssueOpened(payload(issueId), "delivery-1");
		awaitStatus("TRIAGED");
		int requestsAfterFirst = CLAUDE.requestCount();

		orchestrator.handleIssueOpened(payload(issueId), "delivery-2");

		assertThat(count("issues")).isEqualTo(1);
		assertThat(CLAUDE.requestCount()).isEqualTo(requestsAfterFirst);
	}

	@Test
	void dropsEventForUnknownInstallation() {
		orchestrator.handleIssueOpened(payload(ISSUE_IDS.incrementAndGet()), "delivery-1");

		assertThat(count("issues")).isZero();
	}

	@Test
	void dropsEventAfterUninstallButKeepsHistory() {
		insertInstallation(INSTALLATION);
		orchestrator.handleIssueOpened(payload(ISSUE_IDS.incrementAndGet()), "delivery-1");
		awaitStatus("TRIAGED");

		orchestrator.handleInstallationDeleted(installationEvent());
		orchestrator.handleIssueOpened(payload(ISSUE_IDS.incrementAndGet()), "delivery-2");

		assertThat(count("issues")).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT uninstalled_at IS NOT NULL FROM github_installations", Boolean.class)).isTrue();
	}

	@Test
	void rejectsIssueEventWithMissingFields() {
		GitHubWebhookPayload payload = new GitHubWebhookPayload("opened", null, null, null, new GitHubInstallationDto(INSTALLATION));

		assertThatThrownBy(() -> orchestrator.handleIssueOpened(payload, "delivery-1"))
			.isInstanceOf(MalformedWebhookException.class);
	}

	private void awaitStatus(String status) {
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
			assertThat(jdbcTemplate.queryForObject("SELECT status FROM issues", String.class)).isEqualTo(status));
	}

	private int count(String table) {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
	}

	private GitHubWebhookPayload installationEvent() {
		return new GitHubWebhookPayload("deleted", null, null, null, new GitHubInstallationDto(INSTALLATION));
	}

	private GitHubWebhookPayload payload(long issueId) {
		GitHubSenderDto user = new GitHubSenderDto(1L, "octocat", "https://avatars.githubusercontent.com/u/1");
		return new GitHubWebhookPayload(
			"opened",
			new GitHubIssueDto(issueId, 42, "Something broke", "It's broken.", "open", "https://github.com/octocat/hello-world/issues/42", user),
			new GitHubRepositoryDto(1L, "hello-world", "octocat/hello-world", "https://github.com/octocat/hello-world"),
			user,
			new GitHubInstallationDto(INSTALLATION)
		);
	}

}
