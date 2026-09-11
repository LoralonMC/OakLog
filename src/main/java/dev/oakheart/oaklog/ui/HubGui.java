package dev.oakheart.oaklog.ui;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.PlayerProgress;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public class HubGui extends OakLogGui {

    private static final int PLAYER_HEAD_SLOT = 4;

    // The eight vanilla-derived categories sit 4-per-row across rows 1 and 2 with
    // one-slot gaps, matching the spread rhythm of the action-button row below.
    // CUSTOM is deliberately set apart, centred on the row beneath them: it holds
    // the server's own content rather than anything Minecraft ships.
    private static final Map<Integer, Category> CATEGORY_SLOTS;

    static {
        CATEGORY_SLOTS = Map.of(
                10, Category.BLOCKS,
                12, Category.ITEMS,
                14, Category.ENTITIES,
                16, Category.BIOMES,
                19, Category.STRUCTURES,
                21, Category.EFFECTS,
                23, Category.ENCHANTMENTS,
                25, Category.ADVANCEMENTS,
                31, Category.CUSTOM
        );
    }

    private static final int BTN_RECENT = 37;
    private static final int BTN_SETS = 39;
    private static final int BTN_SEARCH = 41;
    private static final int BTN_LEADERBOARD = 43;
    private static final int BTN_CLOSE = 53;

    private final IconRenderer iconRenderer;

    public HubGui(OakLog plugin, Player viewer, UUID targetUuid, IconRenderer iconRenderer) {
        super(plugin, viewer, targetUuid);
        this.iconRenderer = iconRenderer;
    }

    @Override
    public void open() {
        inventory = Bukkit.createInventory(this, 54, plugin.getMessageManager()
                .parse("gui.hub.title")
                .orElse(net.kyori.adventure.text.Component.text("Collection Log")));

        renderPlayerHead();
        renderCategoryButtons();
        renderActionButtons();
        renderCloseButton();

        viewer.openInventory(inventory);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        if (slot == BTN_CLOSE) {
            viewer.closeInventory();
            return;
        }

        Category category = CATEGORY_SLOTS.get(slot);
        if (category != null) {
            new CategoryGui(plugin, viewer, targetUuid, iconRenderer, category, 0).open();
            return;
        }

        if (slot == BTN_RECENT) {
            new RecentGui(plugin, viewer, targetUuid, iconRenderer).open();
            return;
        }
        if (slot == BTN_SETS) {
            new SetsOverviewGui(plugin, viewer, targetUuid, iconRenderer).open();
            return;
        }
        if (slot == BTN_LEADERBOARD) {
            new LeaderboardGui(plugin, viewer, targetUuid, iconRenderer).open();
            return;
        }
        if (slot == BTN_SEARCH) {
            viewer.closeInventory();
            plugin.getSearchInput().prompt(viewer,
                    query -> {
                        if (query == null || query.isBlank()) {
                            new HubGui(plugin, viewer, targetUuid, iconRenderer).open();
                            return;
                        }
                        new SearchResultsGui(plugin, viewer, targetUuid, iconRenderer, query.trim()).open();
                    },
                    () -> new HubGui(plugin, viewer, targetUuid, iconRenderer).open());
        }
    }

    private void renderPlayerHead() {
        int collected = plugin.getProgressManager().collectedCount(targetUuid);
        int live = plugin.getEntryRegistry().liveCount();
        String percent = live == 0 ? "0.0" : String.format("%.1f", 100.0 * collected / live);
        org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(targetUuid);
        String name = offline.getName() != null ? offline.getName() : "Unknown";

        TagResolver[] resolvers = new TagResolver[]{
                Placeholder.unparsed("player", name),
                Placeholder.unparsed("collected", String.valueOf(collected)),
                Placeholder.unparsed("total", String.valueOf(live)),
                Placeholder.unparsed("percent", percent)
        };

        ItemStack head = iconRenderer.buildButton(
                Material.PLAYER_HEAD, "gui.hub.player-head-name", "gui.hub.player-head-lore", resolvers);
        if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) {
            skull.setOwningPlayer(offline);
            head.setItemMeta(skull);
        }
        inventory.setItem(PLAYER_HEAD_SLOT, head);
    }

    private void renderCategoryButtons() {
        for (Map.Entry<Integer, Category> e : CATEGORY_SLOTS.entrySet()) {
            Category category = e.getValue();
            int total = plugin.getEntryRegistry().liveCountFor(category);
            int collected = plugin.getProgressManager().collectedCount(targetUuid, category);

            TagResolver[] resolvers = new TagResolver[]{
                    Placeholder.unparsed("category", plugin.getCategoryDisplay().name(category)),
                    Placeholder.unparsed("collected", String.valueOf(collected)),
                    Placeholder.unparsed("total", String.valueOf(total))
            };
            ItemStack icon = iconRenderer.buildButton(
                    plugin.getCategoryDisplay().icon(category),
                    "gui.hub.category-name",
                    "gui.hub.category-lore",
                    resolvers);
            inventory.setItem(e.getKey(), icon);
        }
    }

    private void renderActionButtons() {
        inventory.setItem(BTN_RECENT, iconRenderer.buildButton(
                Material.CLOCK, "gui.button.recent", null));
        inventory.setItem(BTN_SETS, iconRenderer.buildButton(
                Material.CHEST, "gui.button.sets", null));
        inventory.setItem(BTN_SEARCH, iconRenderer.buildButton(
                Material.SPYGLASS, "gui.button.search", null));
        inventory.setItem(BTN_LEADERBOARD, iconRenderer.buildButton(
                Material.PAPER, "gui.button.leaderboard", null));
    }

    private void renderCloseButton() {
        inventory.setItem(BTN_CLOSE, iconRenderer.buildButton(
                Material.BARRIER, "gui.button.close", null));
    }
}
