package org.linuxdo.oauthlink.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Persisted account link metadata.
 * Exposed to downstream plugins via the public API.
 * Does NOT contain access tokens.
 *
 * <p>All profile fields (displayName, trustLevel) are snapshots
 * captured at link time and may not reflect the current LinuxDO state.
 *
 * <p>{@link #rawProfileJson()} stores the complete user-info API response
 * for maximum extensibility. Downstream plugins can parse any field from it.
 * May be empty string for accounts linked before this feature was added.
 */
public record LinkedAccount(
        UUID playerId,
        String playerName,
        String linuxDoId,
        String linuxDoUsername,
        String linuxDoDisplayName,
        int trustLevel,
        int likesReceived,
        String rawProfileJson,
        Instant linkedAt,
        Instant tokenExpiresAt) {

    /** Unknown/missing sentinel value for trustLevel and likesReceived. */
    public static final int UNKNOWN = -1;

    private static final String LINUXDO_BASE_URL = "https://linux.do";
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Backward-compatible constructor for old code / old YAML data.
     * Defaults trustLevel and likesReceived to UNKNOWN, displayName to username,
     * rawProfileJson to empty.
     */
    public LinkedAccount(UUID playerId, String playerName, String linuxDoId,
                         String linuxDoUsername, Instant linkedAt, Instant tokenExpiresAt) {
        this(playerId, playerName, linuxDoId, linuxDoUsername, linuxDoUsername,
                UNKNOWN, UNKNOWN, "", linkedAt, tokenExpiresAt);
    }

    /**
     * Checks if the linked account's token has expired.
     */
    public boolean isTokenExpired(Clock clock, Duration skew) {
        return Instant.now(clock).isAfter(tokenExpiresAt.plus(skew));
    }

    /**
     * Returns true if this account is still considered active (token not expired).
     */
    public boolean isActive(Clock clock, Duration skew) {
        return !isTokenExpired(clock, skew);
    }

    /**
     * Derived profile URL — not persisted, constructed from username.
     * Format: https://linux.do/u/{username}/summary
     */
    public String getProfileUrl() {
        return LINUXDO_BASE_URL + "/u/" + linuxDoUsername + "/summary";
    }

    /**
     * Human-readable trust level label. Returns "TL0"–"TL4" or "未知".
     * Parsed from explicit field; falls back to rawProfileJson if field is UNKNOWN.
     */
    public String getTrustLevelLabel() {
        int tl = trustLevel;
        if (tl == UNKNOWN && !rawProfileJson.isEmpty()) {
            tl = parseFromRawJson("trust_level", UNKNOWN);
        }
        return tl >= 0 && tl <= 4 ? "TL" + tl : "未知";
    }

    /**
     * 社区分数 — 获赞数。Returns the count or "N/A" if unknown.
     * Parsed from explicit field; falls back to rawProfileJson if field is UNKNOWN.
     */
    public String getLikesReceivedLabel() {
        int lr = likesReceived;
        if (lr == UNKNOWN && !rawProfileJson.isEmpty()) {
            lr = parseFromRawJson("likes_received", UNKNOWN);
        }
        return lr >= 0 ? String.valueOf(lr) : "N/A";
    }

    /**
     * Parses an integer field from the raw profile JSON.
     * Returns defaultValue if the JSON is invalid or the field is missing.
     */
    private int parseFromRawJson(String fieldName, int defaultValue) {
        try {
            JsonNode node = JSON.readTree(rawProfileJson);
            if (node.has(fieldName) && !node.get(fieldName).isNull()) {
                return node.get(fieldName).asInt(defaultValue);
            }
        } catch (Exception ignored) {
            // corrupted JSON or parse error — fall through to default
        }
        return defaultValue;
    }
}
