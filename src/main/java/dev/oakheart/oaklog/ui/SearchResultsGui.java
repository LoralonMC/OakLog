package dev.oakheart.oaklog.ui;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Entry;
import dev.oakheart.oaklog.model.PlayerProgress;
import dev.oakheart.oaklog.util.PaginationMath;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class SearchResultsGui extends OakLogGui {

    private static final int BTN_PREV = 45;
    private static final int BTN_NEXT = 53;
    private static final int BTN_BACK = 49;

    private final IconRenderer iconRenderer;
    private final String query;
    private final List<Entry> matches;
    private int page;

    public SearchResultsGui(OakLog plugin, Player viewer, UUID targetUuid, IconRenderer iconRenderer, String query) {
        super(plugin, viewer, targetUuid);
        this.iconRenderer = iconRenderer;
        this.query = query;
        this.matches = computeMatches(query);
    }

    private List<Entry> computeMatches(String q) {
        String needle = q.toLowerCase(Locale.ROOT);
        int max = plugin.getConfigManager().getSearchMaxResults();
        return plugin.getEntryRegistry().all().stream()
                .filter(e -> !e.isRetired())
                .filter(e -> e.namespacedKey().toLowerCase(Locale.ROOT).contains(needle)
                        || e.displayName().toLowerCase(Locale.ROOT).contains(needle))
                .limit(Math.max(max, PaginationMath.ENTRIES_PER_PAGE))
                .toList();
    }

    @Override
    public void open() {
        inventory = Bukkit.createInventory(this, 54, plugin.getMessageManager()
                .parse("gui.search.title", Placeholder.unparsed("query", query))
                .orElse(net.kyori.adventure.text.Component.text("Search: " + query)));

        PlayerProgress progress = plugin.getProgressManager().get(targetUuid);
        int totalPages = PaginationMath.totalPages(matches.size());
        page = Math.max(0, Math.min(page, totalPages - 1));
        int start = page * PaginationMath.ENTRIES_PER_PAGE;
        int end = Math.min(start + PaginationMath.ENTRIES_PER_PAGE, matches.size());

        for (int i = 0; i < PaginationMath.ENTRIES_PER_PAGE; i++) {
            int slot = i;
            if (start + i >= end) {
                inventory.setItem(slot, null);
                continue;
            }
            inventory.setItem(slot, iconRenderer.build(matches.get(start + i), progress));
        }

        inventory.setItem(BTN_PREV, iconRenderer.buildButton(
                Material.ARROW, "gui.button.prev-page", null));
        inventory.setItem(BTN_NEXT, iconRenderer.buildButton(
                Material.ARROW, "gui.button.next-page", null));
        inventory.setItem(BTN_BACK, iconRenderer.buildButton(
                Material.OAK_DOOR, "gui.button.back-to-hub", null));

        viewer.openInventory(inventory);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        if (slot == BTN_BACK) {
            new HubGui(plugin, viewer, targetUuid, iconRenderer).open();
            return;
        }
        int totalPages = PaginationMath.totalPages(matches.size());
        if (slot == BTN_PREV && page > 0) { page--; open(); }
        else if (slot == BTN_NEXT && page < totalPages - 1) { page++; open(); }
    }
}
