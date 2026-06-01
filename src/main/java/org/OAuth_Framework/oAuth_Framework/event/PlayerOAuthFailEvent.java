package org.OAuth_Framework.oAuth_Framework.event;

import org.OAuth_Framework.oAuth_Framework.oauth.OAuthError;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired on the main thread when an OAuth linking attempt fails.
 * Uses UUID instead of Player because the player may be offline at callback time.
 *
 * <p>Downstream plugins can listen to this event to show error messages or clean up state.
 */
public class PlayerOAuthFailEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final UUID playerId;
    private final OAuthError error;
    private final String safeMessage;
    private boolean cancelled;

    public PlayerOAuthFailEvent(@NotNull UUID playerId, @NotNull OAuthError error,
                                 @NotNull String safeMessage) {
        this.playerId = playerId;
        this.error = error;
        this.safeMessage = safeMessage;
    }

    /**
     * @return the UUID of the player who attempted to link
     */
    @NotNull
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * @return the error type for programmatic handling
     */
    @NotNull
    public OAuthError getError() {
        return error;
    }

    /**
     * @return a player-safe message suitable for display in chat
     */
    @NotNull
    public String getSafeMessage() {
        return safeMessage;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return handlers;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return handlers;
    }
}
