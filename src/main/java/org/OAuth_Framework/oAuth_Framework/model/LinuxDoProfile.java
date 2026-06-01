package org.OAuth_Framework.oAuth_Framework.model;

import org.OAuth_Framework.oAuth_Framework.oauth.OAuthError;
import org.OAuth_Framework.oAuth_Framework.oauth.OAuthException;

/**
 * Normalized LinuxDO user identity from the user-info endpoint.
 * Internal DTO — never persisted.
 *
 * <p>trustLevel reflects the Discourse trust level (0-4).
 * A value of -1 means the field was not available in the API response.
 *
 * <p>likesReceived is the community score (number of likes received).
 * A value of -1 means the field was not available.
 *
 * <p>rawJson is the complete user-info API response body,
 * preserved for downstream extensibility.
 */
public record LinuxDoProfile(String id, String username, String displayName,
                              int trustLevel, int likesReceived, String rawJson) {

    /** Unknown/missing sentinel value for trustLevel and likesReceived. */
    public static final int UNKNOWN = -1;

    public LinuxDoProfile {
        if (id == null || id.isBlank()) {
            throw new OAuthException(OAuthError.PROFILE_INVALID, "用户 ID 缺失");
        }
        if (username == null) {
            username = "";
        }
        if (displayName == null) {
            displayName = username;
        }
        if (rawJson == null) {
            rawJson = "";
        }
    }

    /**
     * Backward-compatible constructor without trustLevel, likesReceived, and rawJson.
     */
    public LinuxDoProfile(String id, String username, String displayName) {
        this(id, username, displayName, UNKNOWN, UNKNOWN, "");
    }

    /** Human-readable trust level label. */
    public String trustLevelLabel() {
        return trustLevel >= 0 && trustLevel <= 4 ? "TL" + trustLevel : "未知";
    }
}
