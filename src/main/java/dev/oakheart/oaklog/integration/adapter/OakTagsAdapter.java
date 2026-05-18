package dev.oakheart.oaklog.integration.adapter;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.EntryDescriptor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Integration with OakTags via {@code OakTagsAPI}. OakTags does not currently
 * expose a public voucher builder, so this adapter constructs a representative
 * voucher ItemStack from the public {@code TagInfo} record (material + display
 * + lore). The voucher in a player's actual inventory may use a different
 * material per the plugin's voucher-config; the log displays a canonical
 * "this is the Cool Tag voucher" view.
 */
public class OakTagsAdapter implements EntryAdapter {

    private static final String NAMESPACE = "oaktags";
    private static final NamespacedKey PDC_KEY =
            new NamespacedKey(NAMESPACE, "tag_voucher_id");
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final OakLog plugin;
    private final Logger logger;

    private Plugin host;
    private Object api;
    private Method getAllTagsMethod;
    private Method tagInfoIdMethod;
    private Method tagInfoDisplayMethod;
    private Method tagInfoMaterialMethod;
    private Method tagInfoLoreMethod;

    private final Map<String, ItemStack> iconCache = new ConcurrentHashMap<>();

    public OakTagsAdapter(OakLog plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    @Override
    public String id() {
        return NAMESPACE;
    }

    @Override
    public boolean isAvailable() {
        host = findHostPlugin();
        if (host == null) return false;
        try {
            ClassLoader cl = host.getClass().getClassLoader();
            Class<?> apiClass = Class.forName("dev.oakheart.oaktags.api.OakTagsAPI", false, cl);
            api = apiClass.getMethod("getInstance").invoke(null);
            if (api == null) return false;
            getAllTagsMethod = apiClass.getMethod("getAllTags");

            Class<?> tagInfoClass = Class.forName("dev.oakheart.oaktags.api.TagInfo", false, cl);
            tagInfoIdMethod = tagInfoClass.getMethod("id");
            tagInfoDisplayMethod = tagInfoClass.getMethod("display");
            tagInfoMaterialMethod = tagInfoClass.getMethod("material");
            tagInfoLoreMethod = tagInfoClass.getMethod("lore");
            return true;
        } catch (Throwable t) {
            logger.log(Level.FINE, "OakTags adapter unavailable: " + t.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public List<EntryDescriptor> enumerate() {
        if (api == null) return List.of();
        try {
            Object result = getAllTagsMethod.invoke(api);
            if (!(result instanceof Collection<?> coll)) return List.of();

            List<EntryDescriptor> out = new ArrayList<>();
            for (Object tagInfo : coll) {
                String tagId = ((String) tagInfoIdMethod.invoke(tagInfo)).toLowerCase(Locale.ROOT);
                String displayMm = (String) tagInfoDisplayMethod.invoke(tagInfo);
                Material material = (Material) tagInfoMaterialMethod.invoke(tagInfo);
                @SuppressWarnings("unchecked")
                List<String> loreLines = (List<String>) tagInfoLoreMethod.invoke(tagInfo);

                Component displayComp = displayMm == null
                        ? Component.text(tagId)
                        : MM.deserialize(displayMm);
                String plainDisplay = PlainTextComponentSerializer.plainText()
                        .serialize(displayComp).trim();
                if (plainDisplay.isEmpty()) plainDisplay = tagId;

                ItemStack icon = buildIcon(material, displayComp, loreLines, tagId);
                iconCache.put(tagId, icon);

                out.add(new EntryDescriptor(
                        NAMESPACE,
                        tagId,
                        Category.ITEMS,
                        plainDisplay,
                        material == null ? "NAME_TAG" : material.name(),
                        null,
                        null
                ));
            }
            logger.info("OakTags adapter: enumerated " + out.size() + " tag vouchers");
            return out;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "OakTags enumerate failed", t);
            return List.of();
        }
    }

    @Override
    public String detect(ItemStack stack) {
        try {
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) return null;
            String tagId = meta.getPersistentDataContainer().get(PDC_KEY, PersistentDataType.STRING);
            if (tagId == null || tagId.isBlank()) return null;
            // Cache the live voucher so subsequent renders show the real player-held look.
            iconCache.put(tagId.toLowerCase(Locale.ROOT), stack.clone());
            return NAMESPACE + ":" + tagId.toLowerCase(Locale.ROOT);
        } catch (Throwable t) {
            return null;
        }
    }

    @Override
    public Optional<ItemStack> resolveLiveIcon(String entryNamespacedKey) {
        if (entryNamespacedKey == null || !entryNamespacedKey.startsWith(NAMESPACE + ":")) {
            return Optional.empty();
        }
        String id = entryNamespacedKey.substring(NAMESPACE.length() + 1);
        ItemStack cached = iconCache.get(id);
        return cached == null ? Optional.empty() : Optional.of(cached.clone());
    }

    private ItemStack buildIcon(Material material, Component displayName, List<String> loreLines, String tagId) {
        if (material == null) material = Material.NAME_TAG;
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.displayName(displayName.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
        if (loreLines != null && !loreLines.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(MM.deserialize(line, Placeholder.unparsed("tag", tagId))
                        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
            meta.lore(lore);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private Plugin findHostPlugin() {
        for (Plugin p : plugin.getServer().getPluginManager().getPlugins()) {
            if (p.getClass().getName().startsWith("dev.oakheart.oaktags")) return p;
        }
        return null;
    }
}
