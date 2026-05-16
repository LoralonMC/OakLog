package dev.oakheart.oaklog.progress;

import dev.oakheart.oaklog.model.Entry;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired (on the main thread) whenever a player newly collects an entry.
 * Other plugins can listen to award rewards, fire titles, etc.
 */
public class ProgressEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Entry entry;
    private final long collectedAt;

    public ProgressEvent(Player player, Entry entry, long collectedAt) {
        this.player = player;
        this.entry = entry;
        this.collectedAt = collectedAt;
    }

    public Player getPlayer() {
        return player;
    }

    public Entry getEntry() {
        return entry;
    }

    public long getCollectedAt() {
        return collectedAt;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
