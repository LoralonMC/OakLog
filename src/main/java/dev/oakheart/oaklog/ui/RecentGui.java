package dev.oakheart.oaklog.ui;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Entry;
import dev.oakheart.oaklog.model.PlayerProgress;
import dev.oakheart.oaklog.model.RecentEntry;
import dev.oakheart.oaklog.util.PaginationMath;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RecentGui extends OakLogGui {

    private static final int BTN_PREV = 45;
    private static final int BTN_NEXT = 53;
    private static final int BTN_BACK = 49;

    private final IconRenderer iconRenderer;
    private int page;

    public RecentGui(OakLog plugin, Player viewer, UUID targetUuid, IconRenderer iconRenderer) {
        super(plugin, viewer, targetUuid);
        this.iconRenderer = iconRenderer;
    }

    @Override
    public void open() {
        inventory = Bukkit.createInventory(this, 54, plugin.getMessageManager()
                .parse("gui.recent.title")
                .orElse(net.kyori.adventure.text.Component.text("Recent Activity")));

        renderEntries();
        renderBottomRow();
        viewer.openInventory(inventory);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        if (slot == BTN_BACK) {
            new HubGui(plugin, viewer, targetUuid, iconRenderer).open();
            return;
        }
        int total = recentEntries().size();
        int maxPage = PaginationMath.totalPages(total) - 1;
        if (slot == BTN_PREV && page > 0) {
            page--;
            open();
        } else if (slot == BTN_NEXT && page < maxPage) {
            page++;
            open();
        }
    }

    private void renderEntries() {
        List<RecentEntry> recent = recentEntries();
        int totalPages = PaginationMath.totalPages(recent.size());
        page = Math.max(0, Math.min(page, totalPages - 1));

        PlayerProgress progress = plugin.getProgressManager().get(targetUuid);
        int start = page * PaginationMath.ENTRIES_PER_PAGE;
        int end = Math.min(start + PaginationMath.ENTRIES_PER_PAGE, recent.size());

        for (int i = 0; i < PaginationMath.ENTRIES_PER_PAGE; i++) {
            int slot = i;
            if (start + i >= end) {
                inventory.setItem(slot, null);
                continue;
            }
            Entry entry = plugin.getEntryRegistry().get(recent.get(start + i).entryId());
            if (entry == null) {
                inventory.setItem(slot, null);
                continue;
            }
            inventory.setItem(slot, iconRenderer.build(entry, progress));
        }
    }

    private List<RecentEntry> recentEntries() {
        PlayerProgress progress = plugin.getProgressManager().get(targetUuid);
        if (progress == null) return new ArrayList<>();
        return progress.snapshotRecent();
    }

    private void renderBottomRow() {
        int total = recentEntries().size();
        int totalPages = PaginationMath.totalPages(total);
        inventory.setItem(BTN_PREV, iconRenderer.buildButton(
                Material.ARROW, "gui.button.prev-page", null));
        inventory.setItem(BTN_NEXT, iconRenderer.buildButton(
                Material.ARROW, "gui.button.next-page", null));
        inventory.setItem(BTN_BACK, iconRenderer.buildButton(
                Material.OAK_DOOR, "gui.button.back-to-hub", null,
                Placeholder.unparsed("page", String.valueOf(page + 1)),
                Placeholder.unparsed("total_pages", String.valueOf(totalPages))));
    }
}
