package org.OAuth_Framework.oAuth_Framework.oauth;

import org.OAuth_Framework.oAuth_Framework.model.LinuxDoProfile;
import org.OAuth_Framework.oAuth_Framework.model.OAuthTokens;
import org.OAuth_Framework.oAuth_Framework.model.PendingAuthorization;
import org.OAuth_Framework.oAuth_Framework.util.OAuthCodeGenerator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe registry for temporary OAuth state shared between
 * the callback HTTP server and Bukkit commands.
 */
public class PendingOAuthRegistry {

    /** State record — stores player info alongside the CSRF state value */
    public record StateEntry(UUID playerId, String playerName, Instant expiresAt) {
        public boolean isExpired(Clock clock) {
            return Instant.now(clock).isAfter(expiresAt);
        }
    }

    private final ConcurrentMap<String, StateEntry> states;
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
     * Generates a new state value, storing the player's identity alongside it.
     * Returns the state string for use in the authorization URL.
     */
    public String createState(UUID playerId, String playerName) {
        purgeExpired();
        String state = generator.generateState();
        StateEntry entry = new StateEntry(playerId, playerName, Instant.now(clock).plus(stateTtl));
        states.put(state, entry);
        return state;
    }

    /**
     * Validates and consumes a state value.
     * Returns the associated StateEntry (playerId + playerName) on success, null if invalid/expired.
     */
    public StateEntry consumeState(String state) {
        StateEntry entry = states.remove(state);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(clock)) {
            return null;
        }
        return entry;
    }

    // === Manual mode fallback (link code) ===

    /**
     * Stores a completed authorization result and returns a link code
     * for manual mode /linkLD &lt;code&gt;.
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
     * Consumes a manual link code and returns the pending authorization.
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
     */
    public void purgeExpired() {
        Instant now = Instant.now(clock);
        states.entrySet().removeIf(entry -> entry.getValue().isExpired(clock));
        authorizations.entrySet().removeIf(entry -> entry.getValue().isExpired(clock));
    }
}
