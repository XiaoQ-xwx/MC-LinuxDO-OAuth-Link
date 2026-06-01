package org.OAuth_Framework.oAuth_Framework.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Thread-safe generator for OAuth state parameters and manual link codes.
 */
public final class OAuthCodeGenerator {

    private static final int STATE_BYTES = 24;  // 32-char URL-safe base64
    private static final int LINK_CODE_LENGTH = 8;
    private static final String LINK_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SecureRandom random;

    public OAuthCodeGenerator() {
        this.random = new SecureRandom();
    }

    /**
     * Generates a 32-character URL-safe random state string for CSRF protection.
     */
    public String generateState() {
        byte[] bytes = new byte[STATE_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Generates an 8-character alphanumeric link code for manual mode.
     * Uses a readable character set (no 0/O/I/1 to avoid confusion).
     */
    public String generateLinkCode() {
        StringBuilder sb = new StringBuilder(LINK_CODE_LENGTH);
        for (int i = 0; i < LINK_CODE_LENGTH; i++) {
            sb.append(LINK_CODE_CHARS.charAt(random.nextInt(LINK_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
