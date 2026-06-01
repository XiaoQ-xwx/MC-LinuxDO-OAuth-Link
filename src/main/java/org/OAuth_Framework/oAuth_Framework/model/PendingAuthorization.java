package org.OAuth_Framework.oAuth_Framework.model;

import java.time.Clock;
import java.time.Instant;

/**
 * Temporary callback result consumed by /link &lt;code&gt;.
 * Created by the callback handler, consumed by the link command.
 */
public record PendingAuthorization(
        String linkCode,
        LinuxDoProfile profile,
        OAuthTokens tokens,
        Instant expiresAt) {

    /**
     * Checks if this pending authorization has expired.
     */
    public boolean isExpired(Clock clock) {
        return Instant.now(clock).isAfter(expiresAt);
    }
}
