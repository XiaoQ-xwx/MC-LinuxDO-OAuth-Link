package org.OAuth_Framework.oAuth_Framework.event;

import org.OAuth_Framework.oAuth_Framework.model.LinkedAccount;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Fired on the main thread when a player unlinks their LinuxDO account.
 * Downstream plugins MUST listen to this event to cascade-revoke
 * permissions, roles, or whitelist entries granted via the OAuth link.
 *
 * <p>The {@link LinkedAccount} carried is the state just before deletion.
 */
public class PlayerOAuthUnlinkEvent extends Event {

    private static final HandlerList handlers = new HandlerList();
    private final UUID playerId;
    private final String playerName;
    private final String linuxDoId;
    private final String linuxDoUsername;

    public PlayerOAuthUnlinkEvent(@NotNull LinkedAccount account) {
        this.playerId = account.playerId();
        this.playerName = account.playerName();
        this.linuxDoId = account.linuxDoId();
        this.linuxDoUsername = account.linuxDoUsername();
    }

    /**
     * @return the UUID of the player who unlinked
     */
    @NotNull
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * @return the player name at the time of unlinking
     */
    @NotNull
    public String getPlayerName() {
        return playerName;
    }

    /**
     * @return the LinuxDO user ID that was unlinked
     */
    @NotNull
    public String getLinuxDoId() {
        return linuxDoId;
    }

    /**
     * @return the LinuxDO username that was unlinked
     */
    @NotNull
    public String getLinuxDoUsername() {
        return linuxDoUsername;
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
