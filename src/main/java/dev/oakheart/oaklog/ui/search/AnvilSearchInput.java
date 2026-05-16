package dev.oakheart.oaklog.ui.search;

import dev.oakheart.oaklog.OakLog;
import org.bukkit.entity.Player;

import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Anvil-input search is on the v1.1 roadmap. A proper implementation needs
 * either AnvilGUI (extra dependency) or careful NMS-level interception, both
 * out of scope for v1. Falls back cleanly to ChatSearchInput.
 */
public class AnvilSearchInput implements SearchInput {

    private final OakLog plugin;
    private final SearchInput fallback;
    private boolean warned;

    public AnvilSearchInput(OakLog plugin, SearchInput fallback) {
        this.plugin = plugin;
        this.fallback = fallback;
    }

    @Override
    public void prompt(Player player, Consumer<String> onResult, Runnable onCancel) {
        if (!warned) {
            plugin.getLogger().log(Level.INFO,
                    "Anvil-input search is not implemented in v1; falling back to chat.");
            warned = true;
        }
        fallback.prompt(player, onResult, onCancel);
    }
}
