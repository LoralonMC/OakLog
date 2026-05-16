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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SetsOverviewGui extends OakLogGui {

    private static final int BTN_PREV = 45;
    private static final int BTN_NEXT = 53;
    private static final int BTN_BACK = 49;

    private final IconRenderer iconRenderer;
    private int page;
    private Map<Integer, String> slotToSetId = new HashMap<>();

    public SetsOverviewGui(OakLog plugin, Player viewer, UUID targetUuid, IconRenderer iconRenderer) {
        super(plugin, viewer, targetUuid);
        this.iconRenderer = iconRenderer;
    }

    @Override
    public void open() {
        inventory = Bukkit.createInventory(this, 54, plugin.getMessageManager()
                .parse("gui.sets.title")
                .orElse(net.kyori.adventure.text.Component.text("Sets")));

        renderSets();
        renderBottomRow();
        viewer.openInventory(inventory);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        if (slot == BTN_BACK) {
            new HubGui(plugin, viewer, targetUuid, iconRenderer).open();
            return;
        }
        List<SetDefinition> sets = plugin.getSetsConfig().sets();
        int totalPages = PaginationMath.totalPages(sets.size());
        if (slot == BTN_PREV && page > 0) { page--; open(); return; }
        if (slot == BTN_NEXT && page < totalPages - 1) { page++; open(); return; }

        String setId = slotToSetId.get(slot);
        if (setId != null) {
            new SetGui(plugin, viewer, targetUuid, iconRenderer, setId, 0).open();
        }
    }

    private void renderSets() {
        slotToSetId.clear();
        List<SetDefinition> sets = plugin.getSetsConfig().sets();
        int totalPages = PaginationMath.totalPages(sets.size());
        page = Math.max(0, Math.min(page, totalPages - 1));

        PlayerProgress progress = plugin.getProgressManager().get(targetUuid);
        int start = page * PaginationMath.ENTRIES_PER_PAGE;
        int end = Math.min(start + PaginationMath.ENTRIES_PER_PAGE, sets.size());

        for (int i = 0; i < PaginationMath.ENTRIES_PER_PAGE; i++) {
            int slot = i;
            if (start + i >= end) {
                inventory.setItem(slot, null);
                continue;
            }
            SetDefinition def = sets.get(start + i);
            List<Entry> entries = plugin.getEntryRegistry().entriesInSetResolved(def.id());
            int total = entries.size();
            int collected = 0;
            if (progress != null) {
                for (Entry e : entries) {
                    if (progress.isCollected(e.id())) collected++;
                }
            }
            String percent = total == 0 ? "0.0" : String.format("%.1f", 100.0 * collected / total);

            TagResolver[] tags = new TagResolver[]{
                    Placeholder.unparsed("set", def.displayName()),
                    Placeholder.unparsed("collected", String.valueOf(collected)),
                    Placeholder.unparsed("total", String.valueOf(total)),
                    Placeholder.unparsed("percent", percent)
            };
            inventory.setItem(slot, iconRenderer.buildButton(Material.CHEST,
                    "gui.sets.set-name", "gui.sets.set-lore", tags));
            slotToSetId.put(slot, def.id());
        }
    }

    private void renderBottomRow() {
        inventory.setItem(BTN_PREV, iconRenderer.buildButton(
                Material.ARROW, "gui.button.prev-page", null));
        inventory.setItem(BTN_NEXT, iconRenderer.buildButton(
                Material.ARROW, "gui.button.next-page", null));
        inventory.setItem(BTN_BACK, iconRenderer.buildButton(
                Material.OAK_DOOR, "gui.button.back-to-hub", null));
    }
}
