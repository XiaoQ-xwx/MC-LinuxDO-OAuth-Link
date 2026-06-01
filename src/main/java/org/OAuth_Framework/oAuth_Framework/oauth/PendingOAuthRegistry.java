package org.OAuth_Framework.oAuth_Framework.oauth;

import org.OAuth_Framework.oAuth_Framework.model.LinuxDoProfile;
import org.OAuth_Framework.oAuth_Framework.model.OAuthTokens;
import org.OAuth_Framework.oAuth_Framework.model.PendingAuthorization;
import org.OAuth_Framework.oAuth_Framework.util.OAuthCodeGenerator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe registry for temporary OAuth state shared between
 * the callback HTTP server and Bukkit commands.
 */
public class PendingOAuthRegistry {

    private final ConcurrentMap<String, Instant> states;
    private final ConcurrentMap<String, PendingAuthorization> authorizations;
    private final OAuthCodeGenerator generator;
    private final Clock clock;
    private final Duration stateTtl;
    private final Duration linkCodeTtl;

    public PendingOAuthRegistry(OAuthCodeGenerator generator, Clock clock,
                                 Duration stateTtl, Duration linkCodeTtl) {
        this.states = new ConcurrentHashMap<>();
        this.authorizations = new ConcurrentHashMap<>();
        this.generator = generator;
        this.clock = clock;
        this.stateTtl = stateTtl;
        this.linkCodeTtl = linkCodeTtl;
    }

    /**
     * Generates a new state value and stores it with a TTL.
     * Returns the state string for use in the authorization URL.
     */
    public String createState() {
        purgeExpired();
        String state = generator.generateState();
        states.put(state, Instant.now(clock).plus(stateTtl));
        return state;
    }

    /**
     * Validates a state value exists and has not expired.
     * Consumes the state (removes it) on success.
     * Returns true if the state was valid.
     */
    public boolean validateAndConsumeState(String state) {
        Instant expiresAt = states.remove(state);
        if (expiresAt == null) {
            return false;
        }
        return !Instant.now(clock).isAfter(expiresAt);
    }

    /**
     * Stores a completed authorization result and returns a link code
     * for the player to use in-game.
     */
    public String storeAuthorization(LinuxDoProfile profile, OAuthTokens tokens) {
        purgeExpired();
        String linkCode = generator.generateLinkCode();
        PendingAuthorization pending = new PendingAuthorization(
                linkCode, profile, tokens,
                Instant.now(clock).plus(linkCodeTtl));
        authorizations.put(linkCode, pending);
        return linkCode;
    }

    /**
     * Consumes a link code and returns the pending authorization.
     * Returns null if the code is invalid or expired.
     */
    public PendingAuthorization consumeCode(String code) {
        PendingAuthorization pending = authorizations.remove(code);
        if (pending == null) {
            return null;
        }
        if (pending.isExpired(clock)) {
            return null;
        }
        return pending;
    }

    /**
     * Removes all expired entries from both maps.
     * Called automatically by createState and storeAuthorization.
     */
    public void purgeExpired() {
        Instant now = Instant.now(clock);
        states.entrySet().removeIf(entry -> now.isAfter(entry.getValue()));
        authorizations.entrySet().removeIf(entry -> entry.getValue().isExpired(clock));
    }
}
