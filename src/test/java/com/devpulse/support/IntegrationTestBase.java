package com.devpulse.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestBase {

	protected static final FakeHttpServer CLAUDE = startServer();
	protected static final FakeHttpServer GITHUB = startServer();
	protected static final KeyPair GITHUB_APP_KEY = generateKey();

	protected static final String VALID_CLAUDE_RESPONSE = """
		{"id":"msg_1","type":"message","role":"assistant","stop_reason":"end_turn",
		"content":[{"type":"text","text":"{\\"severity\\":\\"HIGH\\",\\"issue_type\\":\\"BUG\\",\\"summary\\":\\"It broke.\\",\\"suggested_labels\\":[\\"bug\\"]}"}]}
		""";

	@Autowired
	protected JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void externalServices(DynamicPropertyRegistry registry) {
		registry.add("anthropic.api.url", CLAUDE::url);
		registry.add("github.app.api-url", GITHUB::url);
		registry.add("github.app.private-key", () -> "-----BEGIN PRIVATE KEY-----\n"
			+ Base64.getMimeEncoder().encodeToString(GITHUB_APP_KEY.getPrivate().getEncoded())
			+ "\n-----END PRIVATE KEY-----\n");
	}

	@BeforeEach
	void resetState() {
		jdbcTemplate.execute("TRUNCATE users CASCADE");
		CLAUDE.delayResponsesBy(Duration.ZERO);
		CLAUDE.respondWith(200, VALID_CLAUDE_RESPONSE);
	}

	protected void insertInstallation(long installationId) {
		jdbcTemplate.update("""
			WITH new_user AS (
				INSERT INTO users (email, password_hash, first_name, last_name)
				VALUES (?, 'hash', 'Test', 'User') RETURNING id
			)
			INSERT INTO github_installations (user_id, installation_id, account_login, account_type, repository_selection)
			SELECT id, ?, 'octocat', 'User', 'all' FROM new_user
			""", "user" + installationId + "@example.com", installationId);
	}

	private static KeyPair generateKey() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static FakeHttpServer startServer() {
		try {
			return new FakeHttpServer();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
