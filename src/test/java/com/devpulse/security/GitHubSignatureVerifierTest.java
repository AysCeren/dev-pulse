package com.devpulse.security;

import com.devpulse.config.GitHubWebhookProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubSignatureVerifierTest {

	// Inputs from GitHub's "Validating webhook deliveries" documentation, with the signature computed separately in Python.
	private static final String SECRET = "It's a Secret to Everybody";
	private static final byte[] PAYLOAD = "Hello, World!".getBytes(StandardCharsets.UTF_8);
	private static final String SIGNATURE = "sha256=757107ea0eb2509fc211221cce984b8a37570b6d7586c22c46f4379c8b043e17";

	private final GitHubSignatureVerifier verifier = new GitHubSignatureVerifier(new GitHubWebhookProperties(SECRET));

	@Test
	void acceptsValidSignature() {
		assertThat(verifier.isValidSignature(PAYLOAD, SIGNATURE)).isTrue();
	}

	@Test
	void rejectsTamperedPayload() {
		assertThat(verifier.isValidSignature("Hello, World?".getBytes(StandardCharsets.UTF_8), SIGNATURE)).isFalse();
	}

	@Test
	void rejectsMissingSignature() {
		assertThat(verifier.isValidSignature(PAYLOAD, null)).isFalse();
	}

	@Test
	void rejectsWrongAlgorithmPrefix() {
		assertThat(verifier.isValidSignature(PAYLOAD, SIGNATURE.replace("sha256=", "sha1="))).isFalse();
	}

	@Test
	void rejectsShorterSignature() {
		assertThat(verifier.isValidSignature(PAYLOAD, SIGNATURE.substring(0, 20))).isFalse();
	}

	@Test
	void verifiesExactBytesEvenWhenTheyAreNotValidUtf8() {
		byte[] notUtf8 = {(byte) 0xC3, (byte) 0x28, (byte) 0xFF};
		byte[] replacementCharacters = new String(notUtf8, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);

		assertThat(replacementCharacters).isNotEqualTo(notUtf8);
		String signature = SupportSignatures.sign(SECRET, notUtf8);

		assertThat(verifier.isValidSignature(notUtf8, signature)).isTrue();
		assertThat(verifier.isValidSignature(replacementCharacters, signature)).isFalse();
	}

}
