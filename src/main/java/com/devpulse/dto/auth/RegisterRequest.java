package com.devpulse.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
	@NotBlank @Email @Size(max = 255) String email,
	// BCrypt only uses the first 72 bytes of a password.
	@NotBlank @Size(min = 12, max = 72) String password,
	@NotBlank @Size(max = 100) String firstName,
	@NotBlank @Size(max = 100) String lastName
) {

	@Override
	public String toString() {
		return "RegisterRequest[email=" + email + "]";
	}

}
