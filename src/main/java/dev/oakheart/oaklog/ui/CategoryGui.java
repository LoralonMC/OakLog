package dev.oakheart.oaklog.ui;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.Entry;
import dev.oakheart.oaklog.model.PlayerProgress;
import dev.oakheart.oaklog.util.PaginationMath;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class CategoryGui extends OakLogGui {

    private static final Map<Integer, Category> TAB_SLOTS = Map.of(
            0, Category.BLOCKS,
            1, Category.ITEMS,
            2, Category.ENTITIES,
            3, Category.BIOMES,
            4, Category.STRUCTURES,
            5, Category.EFFECTS,
            6, Category.ENCHANTMENTS
    );
    private static final int BTN_SEARCH = 7;
    private static final int BTN_BACK = 8;
    private static final EnumMap<Category, Material> CATEGORY_ICONS = new EnumMap<>(Category.class);

    static {
        CATEGORY_ICONS.put(Category.BLOCKS, Material.GRASS_BLOCK);
        CATEGORY_ICONS.put(Category.ITEMS, Material.APPLE);
        CATEGORY_ICONS.put(Category.ENTITIES, Material.ZOMBIE_HEAD);
        CATEGORY_ICONS.put(Category.BIOMES, Material.OAK_SAPLING);
        CATEGORY_ICONS.put(Category.STRUCTURES, Material.STONE_BRICKS);
        CATEGORY_ICONS.put(Category.EFFECTS, Material.POTION);
        CATEGORY_ICONS.put(Category.ENCHANTMENTS, Material.ENCHANTED_BOOK);
    }

    private static final int FILTER_ALL = 47;
    private static final int FILTER_COLLECTED = 49;
    private static final int FILTER_UNCOLLECTED = 51;
    private static final int BTN_PREV_PAGE = 45;
    private static final int BTN_NEXT_PAGE = 53;
    private static final int PAGE_INDICATOR_SLOT = 48;

    private final IconRenderer iconRenderer;
    private Category category;
    private int page;
    private Filter filter = Filter.ALL;

    public CategoryGui(OakLog plugin, Player viewer, UUID targetUuid, IconRenderer iconRenderer,
                       Category category, int page) {
        super(plugin, viewer, targetUuid);
        this.iconRenderer = iconRenderer;
        this.category = category;
        this.page = page;
    }

    @Override
    public void open() {
        TagResolver categoryTag = Placeholder.unparsed("category",
                category.name().charAt(0) + category.name().substring(1).toLowerCase(Locale.ROOT));
        inventory = Bukkit.createInventory(this, 54, plugin.getMessageManager()
                .parse("gui.category.title", categoryTag)
                .orElse(net.kyori.adventure.text.Component.text("Collection Log")));

        renderTabs();
        renderEntries();
        renderBottomRow();

        viewer.openInventory(inventory);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        Category tab = TAB_SLOTS.get(slot);
        if (tab != null) {
            this.category = tab;
            this.page = 0;
            open();
            return;
        }

        if (slot == BTN_BACK) {
            new HubGui(plugin, viewer, targetUuid, iconRenderer).open();
            return;
        }

        if (slot == BTN_SEARCH) {
            viewer.sendMessage(net.kyori.adventure.text.Component.text("Search coming soon!"));
            return;
        }

        if (slot == BTN_PREV_PAGE) {
            if (page > 0) {
                page--;
                open();
            }
            return;
        }

        if (slot == BTN_NEXT_PAGE) {
            int total = filteredEntries().size();
            int maxPage = PaginationMath.totalPages(total) - 1;
            if (page < maxPage) {
                page++;
                open();
            }
            return;
        }

        if (slot == FILTER_ALL) { filter = Filter.ALL; page = 0; open(); return; }
        if (slot == FILTER_COLLECTED) { filter = Filter.COLLECTED; page = 0; open(); return; }
        if (slot == FILTER_UNCOLLECTED) { filter = Filter.UNCOLLECTED; page = 0; open(); return; }
    }

    private void renderTabs() {
        for (Map.Entry<Integer, Category> e : TAB_SLOTS.entrySet()) {
            Material icon = CATEGORY_ICONS.get(e.getValue());
            String nameKey = e.getValue() == this.category ? "gui.tab.active-name" : "gui.tab.name";
            TagResolver tag = Placeholder.unparsed("category",
                    e.getValue().name().charAt(0) + e.getValue().name().substring(1).toLowerCase(Locale.ROOT));
            org.bukkit.inventory.ItemStack tabIcon = iconRenderer.buildButton(icon, nameKey, null, tag);
            if (e.getValue() == this.category) {
                addEnchantGlint(tabIcon);
            }
            inventory.setItem(e.getKey(), tabIcon);
        }
        inventory.setItem(BTN_SEARCH, iconRenderer.buildButton(
                Material.SPYGLASS, "gui.button.search", null));
        inventory.setItem(BTN_BACK, iconRenderer.buildButton(
                Material.ARROW, "gui.button.back", null));
    }

    private static void addEnchantGlint(org.bukkit.inventory.ItemStack stack) {
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        meta.setEnchantmentGlintOverride(true);
        stack.setItemMeta(meta);
    }

    private void renderEntries() {
        List<Entry> filtered = filteredEntries();
        int totalPages = PaginationMath.totalPages(filtered.size());
        page = Math.max(0, Math.min(page, totalPages - 1));

        int start = page * PaginationMath.ENTRIES_PER_PAGE;
        int end = Math.min(start + PaginationMath.ENTRIES_PER_PAGE, filtered.size());

        PlayerProgress progress = plugin.getProgressManager().get(targetUuid);
        for (int i = 0; i < PaginationMath.ENTRIES_PER_PAGE; i++) {
            int slot = PaginationMath.FIRST_ENTRY_SLOT + i;
            if (start + i >= end) {
                inventory.setItem(slot, null);
                continue;
            }
            Entry entry = filtered.get(start + i);
            inventory.setItem(slot, iconRenderer.build(entry, progress));
        }
    }

    private void renderBottomRow() {
        int total = filteredEntries().size();
        int totalPages = PaginationMath.totalPages(total);

        TagResolver pageTag = Placeholder.unparsed("page", String.valueOf(page + 1));
        TagResolver totalTag = Placeholder.unparsed("total_pages", String.valueOf(totalPages));

        inventory.setItem(BTN_PREV_PAGE, iconRenderer.buildButton(
                Material.ARROW, "gui.button.prev-page", "gui.button.prev-page-lore", pageTag, totalTag));
        inventory.setItem(BTN_NEXT_PAGE, iconRenderer.buildButton(
                Material.ARROW, "gui.button.next-page", "gui.button.next-page-lore", pageTag, totalTag));

        inventory.setItem(PAGE_INDICATOR_SLOT, iconRenderer.buildButton(
                Material.PAPER, "gui.button.page-indicator", null, pageTag, totalTag));

        inventory.setItem(FILTER_ALL, iconRenderer.buildButton(
                filter == Filter.ALL ? Material.LIME_DYE : Material.WHITE_DYE,
                "gui.button.filter-all", null));
        inventory.setItem(FILTER_COLLECTED, iconRenderer.buildButton(
                filter == Filter.COLLECTED ? Material.LIME_DYE : Material.WHITE_DYE,
                "gui.button.filter-collected", null));
        inventory.setItem(FILTER_UNCOLLECTED, iconRenderer.buildButton(
                filter == Filter.UNCOLLECTED ? Material.LIME_DYE : Material.WHITE_DYE,
                "gui.button.filter-uncollected", null));
    }

    private List<Entry> filteredEntries() {
        List<Entry> all = plugin.getEntryRegistry().byCategory(category);
        PlayerProgress progress = plugin.getProgressManager().get(targetUuid);
        return switch (filter) {
            case ALL -> all.stream().filter(e -> !e.isRetired()).toList();
            case COLLECTED -> all.stream()
                    .filter(e -> !e.isRetired() && progress != null && progress.isCollected(e.id()))
                    .toList();
            case UNCOLLECTED -> all.stream()
                    .filter(e -> !e.isRetired() && (progress == null || !progress.isCollected(e.id())))
                    .toList();
        };
    }

    private enum Filter { ALL, COLLECTED, UNCOLLECTED }
}
