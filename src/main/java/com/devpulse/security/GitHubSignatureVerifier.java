package com.devpulse.security;

import com.devpulse.config.GitHubWebhookProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class GitHubSignatureVerifier {

	private static final String HMAC_SHA256 = "HmacSHA256";
	private static final String PREFIX = "sha256=";

	private final GitHubWebhookProperties properties;

	/**
	 * Takes raw bytes because GitHub signs the exact bytes it sent, and decoding them to a String can alter them.
	 */
	public boolean isValidSignature(byte[] payload, String signatureHeader) {
		if (signatureHeader == null || !signatureHeader.startsWith(PREFIX) || payload == null) {
			return false;
		}

		String expectedSignature = PREFIX + HexFormat.of().formatHex(hmac(payload));

		// Constant-time, so response timing reveals nothing about how much of the signature matched.
		return MessageDigest.isEqual(
			expectedSignature.getBytes(StandardCharsets.UTF_8),
			signatureHeader.getBytes(StandardCharsets.UTF_8)
		);
	}

	private byte[] hmac(byte[] payload) {
		try {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			mac.init(new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
			return mac.doFinal(payload);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HMAC-SHA256 is unavailable", ex);
		}
	}

}
