package dev.oakheart.oaklog.ui;

import dev.oakheart.message.MessageManager;
import dev.oakheart.oaklog.model.Entry;
import dev.oakheart.oaklog.model.PlayerProgress;
import dev.oakheart.oaklog.util.DateFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class IconRenderer {

    private final MessageManager messages;
    private final DateFormat dateFormat;

    public IconRenderer(MessageManager messages, DateFormat dateFormat) {
        this.messages = messages;
        this.dateFormat = dateFormat;
    }

    public ItemStack build(Entry entry, PlayerProgress progress) {
        boolean collected = progress != null && progress.isCollected(entry.id());
        Long collectedAt = null;
        if (collected) {
            for (var recent : progress.snapshotRecent()) {
                if (recent.entryId() == entry.id()) {
                    collectedAt = recent.collectedAt();
                    break;
                }
            }
        }

        Material material = resolveMaterial(entry.iconMaterial());
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        TagResolver entryTag = Placeholder.unparsed("entry", entry.displayName());
        TagResolver dateTag = Placeholder.unparsed("date",
                collectedAt != null ? dateFormat.format(collectedAt) : "");

        String nameKey = collected ? "gui.entry.name-collected" : "gui.entry.name-uncollected";
        Optional<Component> name = messages.parse(nameKey, entryTag, dateTag);
        meta.displayName(name.orElse(Component.text(entry.displayName())));

        List<Component> lore = new ArrayList<>();
        String loreKey = collected ? "gui.entry.lore-collected" : "gui.entry.lore-uncollected";
        messages.parse(loreKey, entryTag, dateTag).ifPresent(lore::add);
        meta.lore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        if (entry.iconModelData() != null) {
            applyCustomModelData(meta, entry.iconModelData());
        }

        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack buildButton(Material material, String nameKey, String loreKey, TagResolver... resolvers) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        messages.parse(nameKey, resolvers)
                .map(c -> c.decorationIfAbsent(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                        net.kyori.adventure.text.format.TextDecoration.State.FALSE))
                .ifPresent(meta::displayName);

        if (loreKey != null) {
            List<Component> lore = new ArrayList<>();
            messages.parse(loreKey, resolvers).ifPresent(lore::add);
            meta.lore(lore);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }

    @SuppressWarnings("deprecation")
    private static void applyCustomModelData(ItemMeta meta, int modelData) {
        // setCustomModelData(int) is deprecated in favor of the float-list
        // CustomModelDataComponent in 1.21.4+. Plugins that ship integer model
        // data through OakheartLib still want the legacy compatible behavior,
        // so we keep using the integer setter and suppress the warning.
        meta.setCustomModelData(modelData);
    }

    private static Material resolveMaterial(String name) {
        if (name == null) return Material.BARRIER;
        try {
            Material m = Material.valueOf(name.toUpperCase(java.util.Locale.ROOT));
            if (m.isAir()) return Material.BARRIER;
            return m;
        } catch (IllegalArgumentException e) {
            return Material.BARRIER;
        }
    }
}
