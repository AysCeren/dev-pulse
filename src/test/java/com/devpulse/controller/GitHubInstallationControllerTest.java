package com.devpulse.controller;

import com.devpulse.domain.entity.User;
import com.devpulse.domain.entity.UserRole;
import com.devpulse.security.JwtService;
import com.devpulse.support.IntegrationTestBase;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class GitHubInstallationControllerTest extends IntegrationTestBase {

	private static final long INSTALLATION = 555L;
	private static final String ONBOARDING = "http://localhost:3000/onboarding?installation=";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void installUrlRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/v1/github/install-url")).andExpect(status().isUnauthorized());
	}

	@Test
	void installUrlCarriesStateThatIdentifiesTheUser() throws Exception {
		UUID userId = insertUser("dev@example.com");
		String token = jwtService.issueAccessToken(user(userId));

		String body = mockMvc.perform(get("/api/v1/github/install-url").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		String installUrl = jsonMapper.readTree(body).get("installUrl").asString();

		assertThat(installUrl).startsWith("https://github.com/apps/devpulse-test/installations/new?state=");
		String state = UriComponentsBuilder.fromUriString(installUrl).build().getQueryParams().getFirst("state");
		assertThat(jwtService.parseInstallState(state)).contains(userId);
	}

	@Test
	void callbackLinksInstallationVerifiedWithGitHub() throws Exception {
		UUID userId = insertUser("dev@example.com");
		gitHubHasInstallation(Instant.now());

		callback("?installation_id=" + INSTALLATION + "&setup_action=install&state=" + jwtService.issueInstallState(userId))
			.andExpect(status().isFound())
			.andExpect(header().string("Location", ONBOARDING + "success"));

		assertThat(GITHUB.lastRequestPath()).isEqualTo("/app/installations/" + INSTALLATION);
		assertThat(jdbcTemplate.queryForObject("SELECT user_id FROM github_installations", UUID.class)).isEqualTo(userId);
		assertThat(jdbcTemplate.queryForObject("SELECT account_login FROM github_installations", String.class)).isEqualTo("octocat");
		assertThat(jdbcTemplate.queryForObject("SELECT repository_selection FROM github_installations", String.class)).isEqualTo("selected");
	}

	@Test
	void callsGitHubWithAppTokenSignedByPrivateKey() throws Exception {
		UUID userId = insertUser("dev@example.com");
		gitHubHasInstallation(Instant.now());

		callback("?installation_id=" + INSTALLATION + "&state=" + jwtService.issueInstallState(userId))
			.andExpect(status().isFound());

		String appToken = GITHUB.lastRequestHeader("Authorization").substring("Bearer ".length());
		Claims claims = Jwts.parser()
			.verifyWith((PublicKey) GITHUB_APP_KEY.getPublic())
			.build()
			.parseSignedClaims(appToken)
			.getPayload();
		assertThat(claims.getIssuer()).isEqualTo("12345");
		assertThat(claims.getExpiration()).isBefore(Instant.now().plus(Duration.ofMinutes(10)));
	}

	@Test
	void missingInstallationIdIsBadRequestAndCreatesNothing() throws Exception {
		UUID userId = insertUser("dev@example.com");

		callback("?setup_action=install&state=" + jwtService.issueInstallState(userId))
			.andExpect(status().isBadRequest());

		assertThat(installationCount()).isZero();
	}

	@Test
	void cancelledInstallationRedirectsAndCreatesNothing() throws Exception {
		callback("?error=access_denied").andExpect(status().isFound())
			.andExpect(header().string("Location", ONBOARDING + "cancelled"));

		assertThat(installationCount()).isZero();
	}

	@Test
	void requestedInstallationAwaitingApprovalRedirectsAsPending() throws Exception {
		callback("?setup_action=request").andExpect(status().isFound())
			.andExpect(header().string("Location", ONBOARDING + "pending"));
	}

	@Test
	void missingOrInvalidStateIsRejectedWithoutCallingGitHub() throws Exception {
		int requests = GITHUB.requestCount();

		callback("?installation_id=" + INSTALLATION).andExpect(status().isUnauthorized());
		callback("?installation_id=" + INSTALLATION + "&state=forged").andExpect(status().isUnauthorized());

		assertThat(GITHUB.requestCount()).isEqualTo(requests);
		assertThat(installationCount()).isZero();
	}

	@Test
	void accessTokenIsNotAcceptedAsState() throws Exception {
		UUID userId = insertUser("dev@example.com");

		callback("?installation_id=" + INSTALLATION + "&state=" + jwtService.issueAccessToken(user(userId)))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void installationClaimedByAnotherUserIsConflict() throws Exception {
		UUID owner = insertUser("owner@example.com");
		UUID attacker = insertUser("attacker@example.com");
		gitHubHasInstallation(Instant.now());
		callback("?installation_id=" + INSTALLATION + "&state=" + jwtService.issueInstallState(owner)).andExpect(status().isFound());

		callback("?installation_id=" + INSTALLATION + "&state=" + jwtService.issueInstallState(attacker))
			.andExpect(status().isConflict());

		assertThat(jdbcTemplate.queryForObject("SELECT user_id FROM github_installations", UUID.class)).isEqualTo(owner);
	}

	@Test
	void oldUnclaimedInstallationCannotBeClaimed() throws Exception {
		UUID userId = insertUser("dev@example.com");
		gitHubHasInstallation(Instant.now().minus(Duration.ofHours(2)));

		callback("?installation_id=" + INSTALLATION + "&state=" + jwtService.issueInstallState(userId))
			.andExpect(status().isConflict());

		assertThat(installationCount()).isZero();
	}

	@Test
	void ownerCanUpdateTheirOldInstallation() throws Exception {
		UUID userId = insertUser("dev@example.com");
		gitHubHasInstallation(Instant.now());
		callback("?installation_id=" + INSTALLATION + "&state=" + jwtService.issueInstallState(userId)).andExpect(status().isFound());
		gitHubHasInstallation(Instant.now().minus(Duration.ofDays(30)));

		callback("?installation_id=" + INSTALLATION + "&setup_action=update&state=" + jwtService.issueInstallState(userId))
			.andExpect(status().isFound());

		assertThat(installationCount()).isEqualTo(1);
	}

	@Test
	void unknownInstallationIsNotFound() throws Exception {
		UUID userId = insertUser("dev@example.com");
		GITHUB.respondWith(404, "{\"message\":\"Not Found\"}");

		callback("?installation_id=" + INSTALLATION + "&state=" + jwtService.issueInstallState(userId))
			.andExpect(status().isNotFound());

		assertThat(installationCount()).isZero();
	}

	@Test
	void gitHubOutageIsBadGateway() throws Exception {
		UUID userId = insertUser("dev@example.com");
		GITHUB.respondWith(503, "{}");

		callback("?installation_id=" + INSTALLATION + "&state=" + jwtService.issueInstallState(userId))
			.andExpect(status().isBadGateway());
	}

	private void gitHubHasInstallation(Instant createdAt) {
		GITHUB.respondWith(200, """
			{"id":%d,"account":{"login":"octocat","type":"User"},"repository_selection":"selected","created_at":"%s"}
			""".formatted(INSTALLATION, createdAt));
	}

	private ResultActions callback(String query) throws Exception {
		return mockMvc.perform(get("/api/v1/github/callback" + query));
	}

	private int installationCount() {
		return jdbcTemplate.queryForObject("SELECT count(*) FROM github_installations", Integer.class);
	}

	private UUID insertUser(String email) {
		return jdbcTemplate.queryForObject("""
			INSERT INTO users (email, password_hash, first_name, last_name)
			VALUES (?, 'hash', 'Test', 'User') RETURNING id
			""", UUID.class, email);
	}

	private User user(UUID id) {
		User user = new User();
		user.setId(id);
		user.setEmail("dev@example.com");
		user.setRole(UserRole.ROLE_DEVELOPER);
		return user;
	}

}
