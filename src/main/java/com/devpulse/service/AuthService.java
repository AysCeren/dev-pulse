package com.devpulse.service;

import com.devpulse.config.JwtProperties;
import com.devpulse.domain.entity.User;
import com.devpulse.dto.auth.AuthResponse;
import com.devpulse.dto.auth.LoginRequest;
import com.devpulse.dto.auth.RegisterRequest;
import com.devpulse.exception.EmailAlreadyRegisteredException;
import com.devpulse.exception.InvalidCredentialsException;
import com.devpulse.repository.UserRepository;
import com.devpulse.security.JwtService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final JwtProperties jwtProperties;
	private final String dummyPasswordHash;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService, JwtProperties jwtProperties) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.jwtProperties = jwtProperties;
		this.dummyPasswordHash = passwordEncoder.encode("not-a-real-password");
	}

	public AuthResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new EmailAlreadyRegisteredException();
		}

		User user = new User();
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setFirstName(request.firstName());
		user.setLastName(request.lastName());

		try {
			return toResponse(userRepository.saveAndFlush(user));
		}
		catch (DataIntegrityViolationException ex) {
			// Two registrations can both pass the existence check, and the unique constraint stops the second.
			throw new EmailAlreadyRegisteredException();
		}
	}

	public AuthResponse login(LoginRequest request) {
		Optional<User> user = userRepository.findByEmail(normalizeEmail(request.email()));

		// Always run BCrypt, so response time doesn't reveal whether the email exists.
		String hash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
		boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

		if (user.isEmpty() || !passwordMatches) {
			throw new InvalidCredentialsException();
		}
		return toResponse(user.get());
	}

	private AuthResponse toResponse(User user) {
		return new AuthResponse(jwtService.issueAccessToken(user), "Bearer", jwtProperties.accessTokenTtl().toSeconds());
	}

	private String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
