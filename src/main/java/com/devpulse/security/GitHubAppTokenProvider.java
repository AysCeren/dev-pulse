package com.devpulse.security;

import com.devpulse.config.GitHubAppProperties;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Signs the short-lived JWT that authenticates DevPulse to GitHub as the App itself.
 */
@Component
public class GitHubAppTokenProvider {

	// Backdated because GitHub rejects tokens issued "in the future" if the clocks differ slightly.
	private static final Duration CLOCK_SKEW = Duration.ofSeconds(60);
	private static final Duration LIFETIME = Duration.ofMinutes(9);

	private final String appId;
	private final PrivateKey privateKey;

	public GitHubAppTokenProvider(GitHubAppProperties properties) {
		this.appId = properties.id();
		this.privateKey = parsePrivateKey(properties.privateKey());
	}

	public String createAppToken() {
		Instant now = Instant.now();
		return Jwts.builder()
			.issuer(appId)
			.issuedAt(Date.from(now.minus(CLOCK_SKEW)))
			.expiration(Date.from(now.plus(LIFETIME)))
			.signWith(privateKey, Jwts.SIG.RS256)
			.compact();
	}

	/**
	 * Expects PKCS#8. GitHub issues PKCS#1 keys, so convert first with:
	 * openssl pkcs8 -topk8 -nocrypt -in github-app.pem
	 */
	private static PrivateKey parsePrivateKey(String pem) {
		String base64 = pem
			.replace("\\n", "\n")
			.replace("-----BEGIN PRIVATE KEY-----", "")
			.replace("-----END PRIVATE KEY-----", "")
			.replaceAll("\\s", "");
		try {
			return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
		}
		catch (GeneralSecurityException | IllegalArgumentException ex) {
			// The cause can quote key material, so it isn't attached.
			throw new IllegalStateException("GITHUB_APP_PRIVATE_KEY must be an RSA key in PKCS#8 PEM format");
		}
	}

}
