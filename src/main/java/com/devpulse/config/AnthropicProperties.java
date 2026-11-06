package com.devpulse.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("anthropic.api")
public record AnthropicProperties(
	@NotBlank String url,
	@NotBlank String key,
	@NotBlank String version,
	@NotBlank String model,
	@Min(1) int maxTokens,
	@NotBlank String effort,
	@NotNull Duration connectTimeout,
	@NotNull Duration readTimeout
) {

	@Override
	public String toString() {
		return "AnthropicProperties[url=" + url + ", model=" + model + "]";
	}

}
