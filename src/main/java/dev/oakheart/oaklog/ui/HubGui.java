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

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class HubGui extends OakLogGui {

    private static final int PLAYER_HEAD_SLOT = 4;

    // Category icons centered in row 1 (slots 9-17), placed at slots 10-16.
    private static final Map<Integer, Category> CATEGORY_SLOTS;
    private static final EnumMap<Category, Material> CATEGORY_ICONS = new EnumMap<>(Category.class);

    static {
        CATEGORY_SLOTS = Map.of(
                10, Category.BLOCKS,
                11, Category.ITEMS,
                12, Category.ENTITIES,
                13, Category.BIOMES,
                14, Category.STRUCTURES,
                15, Category.EFFECTS,
                16, Category.ENCHANTMENTS
        );
        CATEGORY_ICONS.put(Category.BLOCKS, Material.GRASS_BLOCK);
        CATEGORY_ICONS.put(Category.ITEMS, Material.APPLE);
        CATEGORY_ICONS.put(Category.ENTITIES, Material.ZOMBIE_HEAD);
        CATEGORY_ICONS.put(Category.BIOMES, Material.OAK_SAPLING);
        CATEGORY_ICONS.put(Category.STRUCTURES, Material.STONE_BRICKS);
        CATEGORY_ICONS.put(Category.EFFECTS, Material.POTION);
        CATEGORY_ICONS.put(Category.ENCHANTMENTS, Material.ENCHANTED_BOOK);
    }

    private static final int BTN_RECENT = 28;
    private static final int BTN_SETS = 30;
    private static final int BTN_SEARCH = 32;
    private static final int BTN_LEADERBOARD = 34;
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

        TagResolver[] resolvers = new TagResolver[]{
                Placeholder.unparsed("player", Bukkit.getOfflinePlayer(targetUuid).getName() != null
                        ? Bukkit.getOfflinePlayer(targetUuid).getName() : "Unknown"),
                Placeholder.unparsed("collected", String.valueOf(collected)),
                Placeholder.unparsed("total", String.valueOf(live)),
                Placeholder.unparsed("percent", percent)
        };

        inventory.setItem(PLAYER_HEAD_SLOT, iconRenderer.buildButton(
                Material.PLAYER_HEAD, "gui.hub.player-head-name", "gui.hub.player-head-lore", resolvers));
    }

    private void renderCategoryButtons() {
        PlayerProgress progress = plugin.getProgressManager().get(targetUuid);
        for (Map.Entry<Integer, Category> e : CATEGORY_SLOTS.entrySet()) {
            Category category = e.getValue();
            int total = plugin.getEntryRegistry().liveCountFor(category);
            int collected = progress == null ? 0
                    : plugin.getProgressManager().collectedCount(targetUuid, category);

            TagResolver[] resolvers = new TagResolver[]{
                    Placeholder.unparsed("category", category.name().charAt(0) +
                            category.name().substring(1).toLowerCase(java.util.Locale.ROOT)),
                    Placeholder.unparsed("collected", String.valueOf(collected)),
                    Placeholder.unparsed("total", String.valueOf(total))
            };
            ItemStack icon = iconRenderer.buildButton(
                    CATEGORY_ICONS.get(category),
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
