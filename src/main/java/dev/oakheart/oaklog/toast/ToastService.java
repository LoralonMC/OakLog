package dev.oakheart.oaklog.toast;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.advancements.Advancement;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementDisplay;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementHolder;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementProgress;
import com.github.retrooper.packetevents.protocol.advancements.AdvancementType;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemType;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAdvancements;
import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.config.ConfigManager;
import dev.oakheart.oaklog.integration.adapter.AdapterRegistry;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.Entry;
import dev.oakheart.oaklog.model.PlayerProgress;
import dev.oakheart.oaklog.model.PlayerProgress.ToastState;
import dev.oakheart.oaklog.progress.ProgressEvent;
import dev.oakheart.oaklog.progress.ProgressManager;
import dev.oakheart.oaklog.registry.EntryRegistry;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public class ToastService implements Listener {

    private final OakLog plugin;
    private final ConfigManager configManager;
    private final EntryRegistry registry;
    private final AdapterRegistry adapterRegistry;
    private final Logger logger;
    private boolean available;
    private boolean warned;

    public ToastService(OakLog plugin, ConfigManager configManager, EntryRegistry registry,
                        AdapterRegistry adapterRegistry) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.registry = registry;
        this.adapterRegistry = adapterRegistry;
        this.logger = plugin.getLogger();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onProgress(ProgressEvent event) {
        onEntryCollected(event.getPlayer(), event.getEntry(), plugin.getProgressManager());
    }

    public void initialize() {
        boolean pluginPresent = plugin.getServer().getPluginManager().getPlugin("packetevents") != null;
        if (!pluginPresent) {
            logger.info("PacketEvents not detected — toasts disabled.");
            available = false;
            return;
        }
        try {
            PacketEvents.getAPI();
            available = true;
            logger.info("PacketEvents detected — toast notifications enabled.");
        } catch (Exception e) {
            available = false;
            logger.warning("PacketEvents present but API unavailable: " + e.getMessage());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public void onEntryCollected(Player player, Entry entry, ProgressManager progressManager) {
        if (!available) return;

        PlayerProgress progress = progressManager.get(player.getUniqueId());
        if (progress == null) return;

        ToastState state = progress.toastState();
        if (state == ToastState.OFF) return;

        boolean milestonesOnly = state == ToastState.MILESTONES;

        int collected = progressManager.collectedCount(player.getUniqueId(), entry.category());
        int liveCount = registry.liveCountFor(entry.category());

        boolean categoryComplete = configManager.isToastOnCategoryComplete() && collected == liveCount && liveCount > 0;
        boolean fullComplete = configManager.isToastOnFullLogComplete()
                && progressManager.collectedCount(player.getUniqueId()) == registry.liveCount()
                && registry.liveCount() > 0;
        boolean milestoneCount = configManager.getToastMilestoneEveryN() > 0
                && collected > 0
                && collected % configManager.getToastMilestoneEveryN() == 0;

        // Toast resolution:
        // 1. Full log complete (CHALLENGE) trumps everything
        // 2. Category complete (CHALLENGE)
        // 3. Milestone (GOAL)
        // 4. Per-entry (TASK) when not in milestones-only mode
        if (fullComplete) {
            send(player, entry, AdvancementType.CHALLENGE, "Collection Log complete!", "All entries collected.");
        } else if (categoryComplete) {
            send(player, entry, AdvancementType.CHALLENGE,
                    categoryName(entry.category()) + " category complete!",
                    "Every entry in this category collected.");
        } else if (milestoneCount) {
            send(player, entry, AdvancementType.GOAL,
                    collected + " " + categoryName(entry.category()).toLowerCase() + " collected!",
                    entry.displayName());
        } else if (!milestonesOnly) {
            send(player, entry, AdvancementType.TASK, entry.displayName(), "Collected");
        }
    }

    public void onSetCompleted(Player player, String setDisplayName) {
        if (!available || !configManager.isToastOnSetComplete()) return;
        ToastState state = plugin.getProgressManager().get(player.getUniqueId()).toastState();
        if (state == ToastState.OFF) return;

        sendRaw(player, "minecraft:apple", AdvancementType.GOAL,
                "Set complete: " + setDisplayName, "Every entry in this set collected.");
    }

    private void send(Player player, Entry entry, AdvancementType type, String title, String description) {
        ItemStack icon = resolveIconForToast(entry);
        if (icon != null) {
            sendRawWithIcon(player, icon, type, title, description);
            return;
        }
        String materialName = entry.iconMaterial() != null
                ? entry.iconMaterial().toLowerCase()
                : "barrier";
        sendRaw(player, "minecraft:" + materialName, type, title, description);
    }

    private ItemStack resolveIconForToast(Entry entry) {
        try {
            return adapterRegistry.resolveLiveIcon(entry.namespacedKey())
                    .map(SpigotConversionUtil::fromBukkitItemStack)
                    .orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private void sendRawWithIcon(Player player, ItemStack icon, AdvancementType type,
                                 String title, String description) {
        try {
            ResourceLocation key = new ResourceLocation("oaklog", "toast/" + UUID.randomUUID());
            AdvancementDisplay display = new AdvancementDisplay(
                    Component.text(title),
                    Component.text(description),
                    icon,
                    type,
                    null,
                    true,
                    false,
                    0f,
                    0f
            );

            String criterionId = "oaklog_done";
            Advancement advancement = new Advancement(
                    null,
                    display,
                    java.util.List.of(criterionId),
                    java.util.List.of(java.util.List.of(criterionId)),
                    false
            );
            AdvancementHolder holder = new AdvancementHolder(key, advancement);

            Map<String, AdvancementProgress.CriterionProgress> criteria = new HashMap<>();
            criteria.put(criterionId, new AdvancementProgress.CriterionProgress(System.currentTimeMillis()));
            AdvancementProgress progress = new AdvancementProgress(criteria);

            WrapperPlayServerUpdateAdvancements add = new WrapperPlayServerUpdateAdvancements(
                    false,
                    java.util.List.of(holder),
                    Collections.emptySet(),
                    Map.of(key, progress),
                    true
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, add);
            plugin.debug("Toast packet sent: player=" + player.getName()
                    + " icon=<live custom item> frame=" + type + " title=\"" + title + "\"");

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    WrapperPlayServerUpdateAdvancements remove = new WrapperPlayServerUpdateAdvancements(
                            false,
                            Collections.emptyList(),
                            Set.of(key),
                            Collections.emptyMap(),
                            true
                    );
                    PacketEvents.getAPI().getPlayerManager().sendPacket(player, remove);
                } catch (Throwable t) {
                    logger.warning("Failed to send toast cleanup packet: " + t);
                }
            }, 2L);
        } catch (Throwable t) {
            logger.log(java.util.logging.Level.WARNING, "Failed to send custom-icon toast", t);
        }
    }

    public void sendRaw(Player player, String iconNamespacedName, AdvancementType type, String title, String description) {
        try {
            ItemType iconType = ItemTypes.getByName(iconNamespacedName);
            if (iconType == null) iconType = ItemTypes.getByName("minecraft:barrier");
            ItemStack icon = ItemStack.builder().type(iconType).amount(1).build();

            ResourceLocation key = new ResourceLocation("oaklog", "toast/" + UUID.randomUUID());
            AdvancementDisplay display = new AdvancementDisplay(
                    Component.text(title),
                    Component.text(description),
                    icon,
                    type,
                    null,
                    true,           // showToast
                    false,          // hidden
                    0f,
                    0f
            );

            String criterionId = "oaklog_done";
            Advancement advancement = new Advancement(
                    null,           // parent — null means root
                    display,
                    java.util.List.of(criterionId),
                    java.util.List.of(java.util.List.of(criterionId)),
                    false
            );
            AdvancementHolder holder = new AdvancementHolder(key, advancement);

            Map<String, AdvancementProgress.CriterionProgress> criteria = new HashMap<>();
            criteria.put(criterionId, new AdvancementProgress.CriterionProgress(System.currentTimeMillis()));
            AdvancementProgress progress = new AdvancementProgress(criteria);

            WrapperPlayServerUpdateAdvancements add = new WrapperPlayServerUpdateAdvancements(
                    false,                          // reset
                    java.util.List.of(holder),      // added
                    Collections.emptySet(),         // removed
                    Map.of(key, progress),          // progress
                    true                            // showAdvancements (process the data client-side)
            );
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, add);
            plugin.debug("Toast packet sent: player=" + player.getName()
                    + " icon=" + iconNamespacedName + " frame=" + type + " title=\"" + title + "\"");

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    WrapperPlayServerUpdateAdvancements remove = new WrapperPlayServerUpdateAdvancements(
                            false,
                            Collections.emptyList(),
                            Set.of(key),
                            Collections.emptyMap(),
                            true
                    );
                    PacketEvents.getAPI().getPlayerManager().sendPacket(player, remove);
                } catch (Throwable t) {
                    logger.warning("Failed to send toast cleanup packet: " + t);
                }
            }, 2L);
        } catch (Throwable t) {
            logger.log(java.util.logging.Level.WARNING, "Failed to send toast packet", t);
        }
    }

    public void sendTestToast(Player player) {
        sendRaw(player, "minecraft:diamond", AdvancementType.TASK,
                "Toast test", "If you see this, toasts are working.");
    }

    private static String categoryName(Category category) {
        String name = category.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    private void warnOnce(String message) {
        if (!warned) {
            logger.warning(message);
            warned = true;
        }
    }
}
