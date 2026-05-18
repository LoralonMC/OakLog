package dev.oakheart.oaklog.integration;

import com.nexomc.nexo.api.events.NexoItemsLoadedEvent;
import dev.oakheart.oaklog.OakLog;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Reruns the registry sync once Nexo's items have actually loaded. Nexo's
 * registry is empty when {@code onEnable} fires, so the first sync registers
 * zero Nexo entries — this catches the late-load and fills them in.
 */
public class NexoLoadListener implements Listener {

    private final OakLog plugin;

    public NexoLoadListener(OakLog plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onItemsLoaded(NexoItemsLoadedEvent event) {
        plugin.getLogger().info("NexoItemsLoadedEvent received — resyncing collection log registry");
        plugin.getRegistrySync().run();
        plugin.getLeaderboardManager().invalidate();
    }
}
