package com.devpulse.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("github.webhook")
public record GitHubWebhookProperties(
	@NotBlank String secret
) {

	@Override
	public String toString() {
		return "GitHubWebhookProperties[secret=***]";
	}

}
