package dev.oakheart.oaklog.ui.search;

import dev.oakheart.oaklog.OakLog;
import org.bukkit.entity.Player;

import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Sign-input search is on the v1.1 roadmap. The reflective approach against
 * Paper's openSign / openVirtualSign API has churned across Paper releases,
 * so v1 cleanly falls back to ChatSearchInput when sign is selected.
 */
public class SignSearchInput implements SearchInput {

    private final OakLog plugin;
    private final SearchInput fallback;
    private boolean warned;

    public SignSearchInput(OakLog plugin, SearchInput fallback) {
        this.plugin = plugin;
        this.fallback = fallback;
    }

    @Override
    public void prompt(Player player, Consumer<String> onResult, Runnable onCancel) {
        if (!warned) {
            plugin.getLogger().log(Level.INFO,
                    "Sign-input search is not implemented in v1; falling back to chat.");
            warned = true;
        }
        fallback.prompt(player, onResult, onCancel);
    }
}
