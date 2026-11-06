package com.devpulse.controller;

import com.devpulse.security.SupportSignatures;
import com.devpulse.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class GitHubWebhookControllerTest extends IntegrationTestBase {

	private static final String WEBHOOK_PATH = "/api/v1/webhooks/github";
	private static final String SECRET = "test-webhook-secret";
	private static final long FIXTURE_INSTALLATION = 99L;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void validIssueEventIsAcceptedStoredAndTriaged() throws Exception {
		insertInstallation(FIXTURE_INSTALLATION);

		deliver("issues", fixture("github-issues-opened.json")).andExpect(status().isAccepted());

		await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
			assertThat(jdbcTemplate.queryForObject("SELECT status FROM issues", String.class)).isEqualTo("TRIAGED"));
		assertThat(jdbcTemplate.queryForObject("SELECT title FROM issues", String.class))
			.isEqualTo("Login fails for ünïcödé users");
	}

	@Test
	void invalidSignatureIsRejectedAndNothingIsStored() throws Exception {
		insertInstallation(FIXTURE_INSTALLATION);

		mockMvc.perform(post(WEBHOOK_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.header("X-GitHub-Event", "issues")
				.header("X-Hub-Signature-256", "sha256=0000")
				.content(fixture("github-issues-opened.json")))
			.andExpect(status().isUnauthorized());

		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM issues", Integer.class)).isZero();
	}

	@Test
	void missingSignatureIsRejected() throws Exception {
		mockMvc.perform(post(WEBHOOK_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.header("X-GitHub-Event", "issues")
				.content(fixture("github-issues-opened.json")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void signatureIsCheckedBeforeParsing() throws Exception {
		mockMvc.perform(post(WEBHOOK_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.header("X-GitHub-Event", "issues")
				.header("X-Hub-Signature-256", "sha256=0000")
				.content("not json"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void malformedJsonWithValidSignatureIsBadRequest() throws Exception {
		deliver("issues", "not json".getBytes(StandardCharsets.UTF_8))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("Webhook body is not valid JSON"));
	}

	@Test
	void issueEventMissingFieldsIsBadRequest() throws Exception {
		deliver("issues", "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8))
			.andExpect(status().isBadRequest());
	}

	@Test
	void otherEventsAreAcknowledgedAndIgnored() throws Exception {
		insertInstallation(FIXTURE_INSTALLATION);

		deliver("push", "not parsed".getBytes(StandardCharsets.UTF_8)).andExpect(status().isAccepted());

		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM issues", Integer.class)).isZero();
	}

	@Test
	void otherIssueActionsAreAcknowledgedAndIgnored() throws Exception {
		insertInstallation(FIXTURE_INSTALLATION);
		String closed = new String(fixture("github-issues-opened.json"), StandardCharsets.UTF_8)
			.replace("\"action\": \"opened\"", "\"action\": \"closed\"");

		deliver("issues", closed.getBytes(StandardCharsets.UTF_8)).andExpect(status().isAccepted());

		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM issues", Integer.class)).isZero();
	}

	@Test
	void installationDeletedDeactivatesInstallation() throws Exception {
		insertInstallation(FIXTURE_INSTALLATION);

		deliver("installation", fixture("github-installation-deleted.json")).andExpect(status().isAccepted());

		assertThat(jdbcTemplate.queryForObject("SELECT uninstalled_at IS NOT NULL FROM github_installations", Boolean.class)).isTrue();
	}

	@Test
	void redeliveryIsAcknowledgedWithoutDuplicate() throws Exception {
		insertInstallation(FIXTURE_INSTALLATION);

		deliver("issues", fixture("github-issues-opened.json")).andExpect(status().isAccepted());
		deliver("issues", fixture("github-issues-opened.json")).andExpect(status().isAccepted());

		assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM issues", Integer.class)).isEqualTo(1);
		await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
			assertThat(jdbcTemplate.queryForObject("SELECT status FROM issues", String.class)).isEqualTo("TRIAGED"));
	}

	@Test
	void healthIsPublicAndHidesDetails() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components").doesNotExist());
	}

	@Test
	void everythingElseNeedsAuthentication() throws Exception {
		mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/anything")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/not-an-api-path")).andExpect(status().isUnauthorized());
	}

	private ResultActions deliver(String event, byte[] body) throws Exception {
		return mockMvc.perform(post(WEBHOOK_PATH)
			.contentType(MediaType.APPLICATION_JSON)
			.header("X-GitHub-Event", event)
			.header("X-GitHub-Delivery", "test-delivery")
			.header("X-Hub-Signature-256", SupportSignatures.sign(SECRET, body))
			.content(body));
	}

	private byte[] fixture(String name) throws IOException {
		return new ClassPathResource("fixtures/" + name).getContentAsByteArray();
	}

}
