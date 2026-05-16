package dev.oakheart.oaklog.ui.search;

import dev.oakheart.oaklog.OakLog;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.function.Consumer;

public class ChatSearchInput implements SearchInput {

    private static final long TIMEOUT_TICKS = 600L; // 30 seconds

    private final OakLog plugin;

    public ChatSearchInput(OakLog plugin) {
        this.plugin = plugin;
    }

    @Override
    public void prompt(Player player, Consumer<String> onResult, Runnable onCancel) {
        plugin.getMessageManager().sendCommand(player, "search-prompt");

        SessionListener session = new SessionListener(player.getUniqueId(), onResult, onCancel);
        Bukkit.getPluginManager().registerEvents(session, plugin);
        session.timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> session.cancel("timeout"), TIMEOUT_TICKS);
    }

    private class SessionListener implements Listener {
        private final UUID uuid;
        private final Consumer<String> onResult;
        private final Runnable onCancel;
        private BukkitTask timeout;
        private boolean done;

        SessionListener(UUID uuid, Consumer<String> onResult, Runnable onCancel) {
            this.uuid = uuid;
            this.onResult = onResult;
            this.onCancel = onCancel;
        }

        @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
        public void onChat(AsyncChatEvent event) {
            if (done) return;
            if (!event.getPlayer().getUniqueId().equals(uuid)) return;

            event.setCancelled(true);
            String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
            finish(text);
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            if (done) return;
            if (!event.getPlayer().getUniqueId().equals(uuid)) return;
            cancel("quit");
        }

        void cancel(String reason) {
            if (done) return;
            done = true;
            unregister();
            Bukkit.getScheduler().runTask(plugin, onCancel);
        }

        void finish(String text) {
            if (done) return;
            done = true;
            unregister();
            Bukkit.getScheduler().runTask(plugin, () -> onResult.accept(text));
        }

        void unregister() {
            HandlerList.unregisterAll(this);
            if (timeout != null) {
                timeout.cancel();
                timeout = null;
            }
        }
    }
}
