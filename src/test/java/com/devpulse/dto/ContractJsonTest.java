package com.devpulse.dto;

import com.devpulse.dto.claude.ClaudeApiRequest;
import com.devpulse.dto.claude.ClaudeApiResponse;
import com.devpulse.dto.claude.ClaudeMessage;
import com.devpulse.dto.claude.ClaudeOutputConfig;
import com.devpulse.dto.claude.ClaudeOutputFormat;
import com.devpulse.dto.claude.IssueTriageResult;
import com.devpulse.dto.github.GitHubAppInstallationDto;
import com.devpulse.dto.github.GitHubWebhookPayload;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class ContractJsonTest {

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void parsesIssuesOpenedWebhookAndIgnoresUnknownFields() throws IOException {
		GitHubWebhookPayload payload = jsonMapper.readValue(fixture("github-issues-opened.json"), GitHubWebhookPayload.class);

		assertThat(payload.action()).isEqualTo("opened");
		assertThat(payload.issue().id()).isEqualTo(1234567890L);
		assertThat(payload.issue().title()).isEqualTo("Login fails for ünïcödé users");
		assertThat(payload.issue().htmlUrl()).endsWith("/issues/42");
		assertThat(payload.issue().user().avatarUrl()).contains("avatars");
		assertThat(payload.repository().fullName()).isEqualTo("octocat/hello-world");
		assertThat(payload.installation().id()).isEqualTo(99L);
	}

	@Test
	void parsesInstallationDeletedWebhookWithoutIssue() throws IOException {
		GitHubWebhookPayload payload = jsonMapper.readValue(fixture("github-installation-deleted.json"), GitHubWebhookPayload.class);

		assertThat(payload.action()).isEqualTo("deleted");
		assertThat(payload.issue()).isNull();
		assertThat(payload.installation().id()).isEqualTo(99L);
	}

	@Test
	void parsesAppInstallationResponse() throws IOException {
		GitHubAppInstallationDto installation = jsonMapper.readValue(fixture("github-app-installation.json"), GitHubAppInstallationDto.class);

		assertThat(installation.account().login()).isEqualTo("octocat");
		assertThat(installation.account().type()).isEqualTo("User");
		assertThat(installation.repositorySelection()).isEqualTo("selected");
		assertThat(installation.createdAt()).isEqualTo(Instant.parse("2026-11-06T10:15:30Z"));
	}

	@Test
	void parsesClaudeResponseWithLeadingThinkingBlock() throws IOException {
		ClaudeApiResponse response = jsonMapper.readValue(fixture("claude-message-response.json"), ClaudeApiResponse.class);

		assertThat(response.stopReason()).isEqualTo("end_turn");
		assertThat(response.content()).extracting(ClaudeApiResponse.ContentBlock::type).containsExactly("thinking", "text");

		IssueTriageResult result = jsonMapper.readValue(response.content().get(1).text(), IssueTriageResult.class);

		assertThat(result.severity()).isEqualTo("HIGH");
		assertThat(result.issueType()).isEqualTo("BUG");
		assertThat(result.suggestedLabels()).containsExactly("bug", "auth");
	}

	@Test
	void serializesClaudeRequestWithApiFieldNames() {
		ClaudeApiRequest request = new ClaudeApiRequest(
			"claude-opus-5-5",
			2048,
			"system prompt",
			List.of(new ClaudeMessage("user", "hello")),
			new ClaudeOutputConfig("low", new ClaudeOutputFormat("json_schema", Map.of("type", "object"))),
			"default"
		);

		JsonNode json = jsonMapper.valueToTree(request);

		assertThat(json.get("max_tokens").asInt()).isEqualTo(2048);
		assertThat(json.get("output_config").get("format").get("type").asString()).isEqualTo("json_schema");
		assertThat(json.get("fallbacks").asString()).isEqualTo("default");
	}

	private byte[] fixture(String name) throws IOException {
		return new ClassPathResource("fixtures/" + name).getContentAsByteArray();
	}

}
