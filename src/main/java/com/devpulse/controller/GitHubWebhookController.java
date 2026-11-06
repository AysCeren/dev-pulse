package com.devpulse.controller;

import com.devpulse.dto.github.GitHubWebhookPayload;
import com.devpulse.exception.MalformedWebhookException;
import com.devpulse.security.GitHubSignatureVerifier;
import com.devpulse.service.WebhookProcessingOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/github")
@RequiredArgsConstructor
public class GitHubWebhookController {

	private final GitHubSignatureVerifier signatureVerifier;
	private final WebhookProcessingOrchestrator orchestrator;
	private final JsonMapper jsonMapper;

	@PostMapping
	public ResponseEntity<Void> handleWebhook(
			@RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
			@RequestHeader(value = "X-GitHub-Event", required = false) String eventType,
			@RequestHeader(value = "X-GitHub-Delivery", required = false) String deliveryId,
			@RequestBody byte[] rawPayload) {

		if (!signatureVerifier.isValidSignature(rawPayload, signature)) {
			log.warn("Invalid GitHub webhook signature, delivery {}", deliveryId);
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		if ("issues".equals(eventType)) {
			GitHubWebhookPayload payload = parse(rawPayload);
			if ("opened".equals(payload.action())) {
				orchestrator.handleIssueOpened(payload, deliveryId);
			}
		}
		else if ("installation".equals(eventType)) {
			GitHubWebhookPayload payload = parse(rawPayload);
			if ("deleted".equals(payload.action())) {
				orchestrator.handleInstallationDeleted(payload);
			}
		}

		return ResponseEntity.status(HttpStatus.ACCEPTED).build();
	}

	private GitHubWebhookPayload parse(byte[] rawPayload) {
		try {
			return jsonMapper.readValue(rawPayload, GitHubWebhookPayload.class);
		}
		catch (JacksonException ex) {
			throw new MalformedWebhookException("Webhook body is not valid JSON", ex);
		}
	}

}
