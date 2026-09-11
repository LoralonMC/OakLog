package dev.oakheart.oaklog.ui;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.leaderboard.LeaderboardManager;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.storage.DataStore;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LeaderboardGui extends OakLogGui {

    private static final Map<Integer, Category> TAB_CATEGORY = new java.util.HashMap<>();
    // Nine categories fill the top row exactly (0-8), matching the tab strip in
    // CategoryGui. Total no longer fits there, so it sits beside the viewer's own
    // head at the bottom, where overall standing is already shown.
    private static final int TAB_TOTAL = 48;
    private static final int BTN_BACK = 53;
    private static final int YOU_SLOT = 49;

    static {
        TAB_CATEGORY.put(0, Category.BLOCKS);
        TAB_CATEGORY.put(1, Category.ITEMS);
        TAB_CATEGORY.put(2, Category.ENTITIES);
        TAB_CATEGORY.put(3, Category.BIOMES);
        TAB_CATEGORY.put(4, Category.STRUCTURES);
        TAB_CATEGORY.put(5, Category.EFFECTS);
        TAB_CATEGORY.put(6, Category.ENCHANTMENTS);
        TAB_CATEGORY.put(7, Category.ADVANCEMENTS);
        TAB_CATEGORY.put(8, Category.CUSTOM);

    }

    private final IconRenderer iconRenderer;
    private Category scope; // null = total

    public LeaderboardGui(OakLog plugin, Player viewer, UUID targetUuid, IconRenderer iconRenderer) {
        super(plugin, viewer, targetUuid);
        this.iconRenderer = iconRenderer;
    }

    @Override
    public void open() {
        inventory = Bukkit.createInventory(this, 54, plugin.getMessageManager()
                .parse("gui.leaderboard.title")
                .orElse(net.kyori.adventure.text.Component.text("Leaderboard")));

        renderTabs();
        renderTop();
        renderYou();
        viewer.openInventory(inventory);
    }

    @Override
    public void onClick(int slot, ClickType clickType) {
        if (slot == BTN_BACK) {
            new HubGui(plugin, viewer, targetUuid, iconRenderer).open();
            return;
        }
        if (slot == TAB_TOTAL) { scope = null; open(); return; }
        Category category = TAB_CATEGORY.get(slot);
        if (category != null) { scope = category; open(); }
    }

    private void renderTabs() {
        ItemStack totalTab = iconRenderer.buildButton(
                scope == null ? Material.LIME_DYE : Material.WHITE_DYE,
                "gui.leaderboard.tab-total", null);
        if (scope == null) addEnchantGlint(totalTab);
        inventory.setItem(TAB_TOTAL, totalTab);
        for (Map.Entry<Integer, Category> e : TAB_CATEGORY.entrySet()) {
            String key = scope == e.getValue() ? "gui.leaderboard.tab-active" : "gui.leaderboard.tab";
            TagResolver tag = Placeholder.unparsed("category",
                    plugin.getCategoryDisplay().name(e.getValue()));
            ItemStack tab = iconRenderer.buildButton(
                    plugin.getCategoryDisplay().icon(e.getValue()), key, null, tag);
            if (scope == e.getValue()) addEnchantGlint(tab);
            inventory.setItem(e.getKey(), tab);
        }
        inventory.setItem(BTN_BACK, iconRenderer.buildButton(
                Material.OAK_DOOR, "gui.button.back-to-hub", null));
    }

    private static void addEnchantGlint(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        meta.setEnchantmentGlintOverride(true);
        stack.setItemMeta(meta);
    }

    private void renderTop() {
        LeaderboardManager mgr = plugin.getLeaderboardManager();
        List<DataStore.LeaderboardRow> rows = scope == null ? mgr.topOverall() : mgr.topByCategory(scope);

        int liveTotal = scope == null
                ? plugin.getEntryRegistry().liveCount()
                : plugin.getEntryRegistry().liveCountFor(scope);

        // Top 7 in row 1 (slots 9-15), ranks 8-10 in row 2 (slots 18-20).
        int[] slots = new int[]{9, 10, 11, 12, 13, 14, 15, 18, 19, 20};
        for (int i = 0; i < slots.length; i++) {
            if (i >= rows.size()) {
                inventory.setItem(slots[i], null);
                continue;
            }
            DataStore.LeaderboardRow row = rows.get(i);
            inventory.setItem(slots[i], buildPlayerHead(row.playerUuid(), i + 1, row.collectedCount(), liveTotal));
        }
    }

    private void renderYou() {
        UUID viewerUuid = viewer.getUniqueId();
        LeaderboardManager mgr = plugin.getLeaderboardManager();
        int rank = mgr.rankOverall(viewerUuid);
        int collected = mgr.collectedOverall(viewerUuid);
        int total = plugin.getEntryRegistry().liveCount();
        inventory.setItem(YOU_SLOT, buildPlayerHead(viewerUuid, rank, collected, total));
    }

    private ItemStack buildPlayerHead(UUID uuid, int rank, int collected, int total) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skull) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
            skull.setOwningPlayer(offline);
            String name = offline.getName() != null ? offline.getName() : "Unknown";
            String percent = total == 0 ? "0.0" : String.format("%.1f", 100.0 * collected / total);

            TagResolver[] tags = new TagResolver[]{
                    Placeholder.unparsed("rank", String.valueOf(rank)),
                    Placeholder.unparsed("player", name),
                    Placeholder.unparsed("collected", String.valueOf(collected)),
                    Placeholder.unparsed("total", String.valueOf(total)),
                    Placeholder.unparsed("percent", percent)
            };

            plugin.getMessageManager().parse("gui.leaderboard.entry-name", tags)
                    .map(c -> c.decorationIfAbsent(
                            net.kyori.adventure.text.format.TextDecoration.ITALIC,
                            net.kyori.adventure.text.format.TextDecoration.State.FALSE))
                    .ifPresent(skull::displayName);
            plugin.getMessageManager().parse("gui.leaderboard.entry-lore", tags)
                    .ifPresent(c -> skull.lore(List.of(c)));

            head.setItemMeta(skull);
        }
        return head;
    }
}
