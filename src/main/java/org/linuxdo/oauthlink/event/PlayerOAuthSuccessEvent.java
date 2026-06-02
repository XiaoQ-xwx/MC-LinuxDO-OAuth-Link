package org.linuxdo.oauthlink.event;

import org.linuxdo.oauthlink.model.LinkedAccount;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired on the main thread after a player successfully links their LinuxDO account.
 * Downstream plugins can listen to this event to sync ranks, grant permissions, etc.
 *
 * <p>Does NOT expose access tokens — use {@link #getAccount()} for linked account info.
 */
public class PlayerOAuthSuccessEvent extends Event implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    private final LinkedAccount account;
    private boolean cancelled;

    public PlayerOAuthSuccessEvent(@NotNull LinkedAccount account) {
        this.account = account;
    }

    /**
     * @return the linked account information (no tokens exposed)
     */
    @NotNull
    public LinkedAccount getAccount() {
        return account;
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
