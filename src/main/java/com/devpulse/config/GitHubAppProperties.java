package com.devpulse.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("github.app")
public record GitHubAppProperties(
	@NotBlank String id,
	@NotBlank String slug,
	@NotBlank String privateKey,
	@NotBlank String apiUrl,
	@NotBlank String installUrl,
	@NotBlank String onboardingUrl,
	@NotNull Duration claimWindow,
	@NotNull Duration connectTimeout,
	@NotNull Duration readTimeout
) {

	@Override
	public String toString() {
		return "GitHubAppProperties[id=" + id + ", slug=" + slug + ", privateKey=***]";
	}

}
