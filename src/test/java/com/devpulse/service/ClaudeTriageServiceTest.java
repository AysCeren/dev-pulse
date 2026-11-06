package com.devpulse.service;

import com.devpulse.config.AnthropicProperties;
import com.devpulse.config.ClaudeClientConfig;
import com.devpulse.domain.entity.IssueSeverity;
import com.devpulse.domain.entity.IssueType;
import com.devpulse.exception.TriageFailedException;
import com.devpulse.support.FakeHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeTriageServiceTest {

	private static final String VALID_RESULT = "{\\\"severity\\\":\\\"HIGH\\\",\\\"issue_type\\\":\\\"BUG\\\",\\\"summary\\\":\\\"It broke.\\\",\\\"suggested_labels\\\":[\\\"bug\\\"]}";

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private FakeHttpServer claude;
	private ClaudeTriageService service;

	@BeforeEach
	void setUp() throws IOException {
		claude = new FakeHttpServer();
		AnthropicProperties properties = new AnthropicProperties(
			claude.url(), "test-key", "2026-10-21", "claude-opus-5-5", 2048, "low", Duration.ofSeconds(1), Duration.ofMillis(300)
		);
		service = new ClaudeTriageService(new ClaudeClientConfig().claudeRestClient(properties), properties, jsonMapper);
	}

	@AfterEach
	void tearDown() {
		claude.close();
	}

	@Test
	void returnsClassificationAndKeepsRawText() {
		claude.respondWith(200, message("end_turn", VALID_RESULT));

		TriageCompletion completion = service.triageIssue("Title", "Body");

		assertThat(completion.severity()).isEqualTo(IssueSeverity.HIGH);
		assertThat(completion.issueType()).isEqualTo(IssueType.BUG);
		assertThat(completion.summary()).isEqualTo("It broke.");
		assertThat(completion.suggestedLabels()).containsExactly("bug");
		assertThat(completion.rawResponse()).contains("\"severity\":\"HIGH\"");
	}

	@Test
	void sendsExpectedHeadersAndStructuredOutputRequest() {
		claude.respondWith(200, message("end_turn", VALID_RESULT));

		service.triageIssue("Title", null);

		assertThat(claude.lastRequestPath()).isEqualTo("/v1/messages");
		assertThat(claude.lastRequestHeader("x-api-key")).isEqualTo("test-key");
		assertThat(claude.lastRequestHeader("anthropic-version")).isEqualTo("2026-10-21");
		assertThat(claude.lastRequestHeader("anthropic-beta")).isEqualTo("server-side-fallback-2026-07-01");

		JsonNode request = jsonMapper.readTree(claude.lastRequestBody());
		assertThat(request.get("model").asString()).isEqualTo("claude-opus-5-5");
		assertThat(request.get("fallbacks").asString()).isEqualTo("default");
		assertThat(request.get("output_config").get("effort").asString()).isEqualTo("low");
		assertThat(request.get("output_config").get("format").get("type").asString()).isEqualTo("json_schema");
		assertThat(request.get("output_config").get("format").get("schema").get("additionalProperties").asBoolean()).isFalse();
		assertThat(request.get("messages").get(0).get("content").asString()).contains("No description provided.");
	}

	@Test
	void failsOnRateLimit() {
		claude.respondWith(429, "{\"type\":\"error\",\"error\":{\"type\":\"rate_limit_error\"}}");

		assertFails("call failed");
	}

	@Test
	void failsOnOverload() {
		claude.respondWith(529, "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\"}}");

		assertFails("call failed");
	}

	@Test
	void failsOnTimeout() {
		claude.respondWith(200, message("end_turn", VALID_RESULT));
		claude.delayResponsesBy(Duration.ofSeconds(2));

		assertFails("call failed");
	}

	@ParameterizedTest
	@ValueSource(strings = {"refusal", "max_tokens"})
	void failsWhenModelDidNotFinish(String stopReason) {
		claude.respondWith(200, message(stopReason, VALID_RESULT));

		assertFails(stopReason);
	}

	@Test
	void failsOnMalformedJson() {
		claude.respondWith(200, message("end_turn", "not json"));

		assertFails("malformed");
	}

	@Test
	void failsOnUnknownSeverity() {
		claude.respondWith(200, message("end_turn", VALID_RESULT.replace("HIGH", "URGENT")));

		assertFails("unknown classification");
	}

	@Test
	void failsWhenThereIsNoTextBlock() {
		claude.respondWith(200, "{\"content\":[{\"type\":\"thinking\",\"thinking\":\"\"}],\"stop_reason\":\"end_turn\"}");

		assertFails("no text block");
	}

	private void assertFails(String message) {
		assertThatThrownBy(() -> service.triageIssue("Title", "Body"))
			.isInstanceOf(TriageFailedException.class)
			.hasMessageContaining(message);
	}

	private String message(String stopReason, String text) {
		return """
			{"id":"msg_1","type":"message","role":"assistant","stop_reason":"%s",
			"content":[{"type":"thinking","thinking":""},{"type":"text","text":"%s"}]}
			""".formatted(stopReason, text);
	}

}
