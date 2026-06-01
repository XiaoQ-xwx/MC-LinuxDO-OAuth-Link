package org.OAuth_Framework.oAuth_Framework.model;

import org.OAuth_Framework.oAuth_Framework.oauth.OAuthError;
import org.OAuth_Framework.oAuth_Framework.oauth.OAuthException;

/**
 * Normalized LinuxDO user identity from the user-info endpoint.
 * Internal DTO — never persisted.
 */
public record LinuxDoProfile(String id, String username, String displayName) {

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
    }
}
