package com.devpulse.controller;

import com.devpulse.config.JwtProperties;
import com.devpulse.domain.entity.User;
import com.devpulse.domain.entity.UserRole;
import com.devpulse.security.JwtService;
import com.devpulse.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthControllerTest extends IntegrationTestBase {

	private static final String PASSWORD = "a-long-enough-password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void registerStoresBcryptHashAndDefaultRole() throws Exception {
		register("Dev@Example.com", PASSWORD)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.tokenType").value("Bearer"))
			.andExpect(jsonPath("$.expiresInSeconds").value(3600));

		assertThat(jdbcTemplate.queryForObject("SELECT email FROM users", String.class)).isEqualTo("dev@example.com");
		assertThat(jdbcTemplate.queryForObject("SELECT password_hash FROM users", String.class)).startsWith("$2a$");
		assertThat(jdbcTemplate.queryForObject("SELECT role FROM users", String.class)).isEqualTo("ROLE_DEVELOPER");
	}

	@Test
	void duplicateEmailIsConflictRegardlessOfCase() throws Exception {
		register("dev@example.com", PASSWORD).andExpect(status().isCreated());

		register("DEV@example.com", PASSWORD).andExpect(status().isConflict());
	}

	@Test
	void invalidRegistrationIsBadRequest() throws Exception {
		register("not-an-email", PASSWORD).andExpect(status().isBadRequest());
		register("dev@example.com", "short").andExpect(status().isBadRequest());
		register("dev@example.com", "x".repeat(73)).andExpect(status().isBadRequest());
	}

	@Test
	void loginReturnsTokenThatAuthenticatesRequests() throws Exception {
		register("dev@example.com", PASSWORD).andExpect(status().isCreated());

		String token = token(login("DEV@example.com", PASSWORD).andExpect(status().isOk()));

		// Reaching the 404 means authentication passed, since no route exists at this path.
		mockMvc.perform(get("/api/v1/anything").header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound());
	}

	@Test
	void wrongPasswordAndUnknownEmailGiveTheSameError() throws Exception {
		register("dev@example.com", PASSWORD).andExpect(status().isCreated());

		String wrongPassword = login("dev@example.com", "wrong-password-here").andExpect(status().isUnauthorized())
			.andReturn().getResponse().getContentAsString();
		String unknownEmail = login("nobody@example.com", PASSWORD).andExpect(status().isUnauthorized())
			.andReturn().getResponse().getContentAsString();

		assertThat(unknownEmail).isEqualTo(wrongPassword);
	}

	@Test
	void missingOrInvalidTokensAreRejected() throws Exception {
		mockMvc.perform(get("/api/v1/anything")).andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/v1/anything").header("Authorization", "Bearer not-a-token"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void tamperedTokenIsRejected() throws Exception {
		String token = jwtService.issueAccessToken(user(UserRole.ROLE_DEVELOPER));
		String tampered = token.substring(0, token.length() - 4) + "AAAA";

		mockMvc.perform(get("/api/v1/anything").header("Authorization", "Bearer " + tampered))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void expiredTokenIsRejected() throws Exception {
		JwtProperties expired = new JwtProperties("test-jwt-secret-that-is-at-least-32-bytes-long", Duration.ofSeconds(-120), Duration.ofMinutes(1));
		String token = new JwtService(expired).issueAccessToken(user(UserRole.ROLE_DEVELOPER));

		mockMvc.perform(get("/api/v1/anything").header("Authorization", "Bearer " + token))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void tokenSignedWithAnotherKeyIsRejected() throws Exception {
		JwtProperties other = new JwtProperties("another-secret-that-is-at-least-32-bytes-long", Duration.ofHours(1), Duration.ofMinutes(1));
		String token = new JwtService(other).issueAccessToken(user(UserRole.ROLE_ADMIN));

		mockMvc.perform(get("/api/v1/anything").header("Authorization", "Bearer " + token))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void installStateIsNotAcceptedAsAccessToken() throws Exception {
		String state = jwtService.issueInstallState(UUID.randomUUID());

		mockMvc.perform(get("/api/v1/anything").header("Authorization", "Bearer " + state))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void actuatorMetricsRequireAdminRole() throws Exception {
		String developer = jwtService.issueAccessToken(user(UserRole.ROLE_DEVELOPER));
		String admin = jwtService.issueAccessToken(user(UserRole.ROLE_ADMIN));

		mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer " + developer))
			.andExpect(status().isForbidden());
		mockMvc.perform(get("/actuator/prometheus").header("Authorization", "Bearer " + admin))
			.andExpect(status().isOk());
	}

	private User user(UserRole role) {
		User user = new User();
		user.setId(UUID.randomUUID());
		user.setEmail("dev@example.com");
		user.setRole(role);
		return user;
	}

	private ResultActions register(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/register")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"email":"%s","password":"%s","firstName":"Test","lastName":"User"}
				""".formatted(email, password)));
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/v1/auth/login")
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
				{"email":"%s","password":"%s"}
				""".formatted(email, password)));
	}

	private String token(ResultActions result) throws Exception {
		return jsonMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("accessToken").asString();
	}

}
