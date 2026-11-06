package com.devpulse.dto.auth;

public record AuthResponse(
	String accessToken,
	String tokenType,
	long expiresInSeconds
) {
}
