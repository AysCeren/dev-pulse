package com.devpulse.security;

import com.devpulse.domain.entity.UserRole;

import java.util.UUID;

public record AuthenticatedUser(
	UUID id,
	String email,
	UserRole role
) {
}
