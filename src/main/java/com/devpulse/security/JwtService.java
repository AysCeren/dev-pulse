package com.devpulse.security;

import com.devpulse.config.JwtProperties;
import com.devpulse.domain.entity.User;
import com.devpulse.domain.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtService {

	// Separate audiences stop a token issued for one purpose from being accepted for the other.
	private static final String ACCESS_AUDIENCE = "devpulse-api";
	private static final String INSTALL_STATE_AUDIENCE = "devpulse-github-install";

	private static final String EMAIL_CLAIM = "email";
	private static final String ROLE_CLAIM = "role";

	private final JwtProperties properties;
	private final SecretKey key;

	public JwtService(JwtProperties properties) {
		this.properties = properties;
		this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
	}

	public String issueAccessToken(User user) {
		return Jwts.builder()
			.subject(user.getId().toString())
			.audience().add(ACCESS_AUDIENCE).and()
			.claim(EMAIL_CLAIM, user.getEmail())
			.claim(ROLE_CLAIM, user.getRole().name())
			.issuedAt(new Date())
			.expiration(expiryAfter(properties.accessTokenTtl()))
			.signWith(key)
			.compact();
	}

	public Optional<AuthenticatedUser> parseAccessToken(String token) {
		return parse(token, ACCESS_AUDIENCE).map(claims -> new AuthenticatedUser(
			UUID.fromString(claims.getSubject()),
			claims.get(EMAIL_CLAIM, String.class),
			UserRole.valueOf(claims.get(ROLE_CLAIM, String.class))
		));
	}

	/**
	 * GitHub's redirect back to DevPulse carries no Authorization header, so this signed value
	 * travels through GitHub in the state parameter to identify the user.
	 */
	public String issueInstallState(UUID userId) {
		return Jwts.builder()
			.subject(userId.toString())
			.audience().add(INSTALL_STATE_AUDIENCE).and()
			.issuedAt(new Date())
			.expiration(expiryAfter(properties.installStateTtl()))
			.signWith(key)
			.compact();
	}

	public Optional<UUID> parseInstallState(String state) {
		return parse(state, INSTALL_STATE_AUDIENCE).map(claims -> UUID.fromString(claims.getSubject()));
	}

	private Optional<Claims> parse(String token, String audience) {
		try {
			return Optional.of(Jwts.parser()
				.verifyWith(key)
				.requireAudience(audience)
				.build()
				.parseSignedClaims(token)
				.getPayload());
		}
		catch (JwtException | IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

	private Date expiryAfter(Duration ttl) {
		return Date.from(Instant.now().plus(ttl));
	}

}
