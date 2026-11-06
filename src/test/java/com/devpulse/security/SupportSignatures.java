package com.devpulse.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

public final class SupportSignatures {

	private SupportSignatures() {
	}

	public static String sign(String secret, byte[] payload) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return "sha256=" + HexFormat.of().formatHex(mac.doFinal(payload));
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
