package dev.oakheart.oaklog.ui;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Entry;
import dev.oakheart.oaklog.model.PlayerProgress;
import dev.oakheart.oaklog.model.SetDefinition;
import dev.oakheart.oaklog.util.PaginationMath;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.UUID;

public class SetGui extends OakLogGui {

    private static final int BTN_PREV = 45;
    private static final int BTN_NEXT = 53;
    private static final int BTN_BACK = 49;

    private final IconRenderer iconRenderer;
    private final String setId;
    private int page;

    public SetGui(OakLog plugin, Player viewer, UUID targetUuid, IconRenderer iconRenderer,
                  String setId, int page) {
        super(plugin, viewer, targetUuid);
        this.iconRenderer = iconRenderer;
        this.setId = setId;
        this.page = page;
    }

    @Override
    public void open() {
        SetDefinition def = findDefinition();
        String title = def != null ? def.displayName() : setId;

        inventory = Bukkit.createInventory(this, 54, plugin.getMessageManager()
                .parse("gui.set.title", Placeholder.parsed("set", title))
                .orElse(net.kyori.adventure.text.Component.text(title)));

        renderEntries();
        renderBottomRow();
        viewer.openInventory(inventory);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        if (slot == BTN_BACK) {
            new SetsOverviewGui(plugin, viewer, targetUuid, iconRenderer).open();
            return;
        }
        int total = entries().size();
        int maxPage = PaginationMath.totalPages(total) - 1;
        if (slot == BTN_PREV && page > 0) { page--; open(); }
        else if (slot == BTN_NEXT && page < maxPage) { page++; open(); }
    }

    private List<Entry> entries() {
        return plugin.getEntryRegistry().entriesInSetResolved(setId);
    }

    private SetDefinition findDefinition() {
        for (SetDefinition def : plugin.getSetsConfig().sets()) {
            if (def.id().equals(setId)) return def;
        }
        return null;
    }

    private void renderEntries() {
        List<Entry> all = entries();
        int totalPages = PaginationMath.totalPages(all.size());
        page = Math.max(0, Math.min(page, totalPages - 1));

        PlayerProgress progress = plugin.getProgressManager().get(targetUuid);
        int start = page * PaginationMath.ENTRIES_PER_PAGE;
        int end = Math.min(start + PaginationMath.ENTRIES_PER_PAGE, all.size());

        for (int i = 0; i < PaginationMath.ENTRIES_PER_PAGE; i++) {
            int slot = i;
            if (start + i >= end) {
                inventory.setItem(slot, null);
                continue;
            }
            inventory.setItem(slot, iconRenderer.build(all.get(start + i), progress));
        }
    }

    private void renderBottomRow() {
        int totalPages = PaginationMath.totalPages(entries().size());
        TagResolver pageTag = Placeholder.unparsed("page", String.valueOf(page + 1));
        TagResolver totalTag = Placeholder.unparsed("total_pages", String.valueOf(totalPages));
        inventory.setItem(BTN_PREV, iconRenderer.buildButton(
                Material.ARROW, "gui.button.prev-page", "gui.button.prev-page-lore", pageTag, totalTag));
        inventory.setItem(BTN_NEXT, iconRenderer.buildButton(
                Material.ARROW, "gui.button.next-page", "gui.button.next-page-lore", pageTag, totalTag));
        inventory.setItem(BTN_BACK, iconRenderer.buildButton(
                Material.ARROW, "gui.button.back", null));
    }
}
