package com.devpulse.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("jwt")
public record JwtProperties(
	// HS256 requires a key of at least 256 bits.
	@NotBlank @Size(min = 32) String secret,
	@NotNull Duration accessTokenTtl,
	@NotNull Duration installStateTtl
) {

	@Override
	public String toString() {
		return "JwtProperties[secret=***, accessTokenTtl=" + accessTokenTtl + "]";
	}

}
