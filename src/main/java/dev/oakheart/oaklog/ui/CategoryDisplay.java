package dev.oakheart.oaklog.ui;

import dev.oakheart.oaklog.config.ConfigManager;
import dev.oakheart.oaklog.integration.adapter.AdapterRegistry;
import dev.oakheart.oaklog.model.Category;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Optional;

/**
 * Resolves the player-facing name and icon for a {@link Category}.
 *
 * <p>Every category may be renamed and re-iconed from config, which is what lets
 * a server call the custom category whatever suits it — Oakheart calls it Curios,
 * the code only ever knows it as {@code CUSTOM}. Without an override a category
 * keeps its built-in icon and its capitalised enum name.
 *
 * <p>This is the single source of both. The three GUIs that show category buttons
 * previously each kept their own copy of the icon table, which meant three places
 * to edit and three chances for them to disagree.
 */
public final class CategoryDisplay {

    private static final EnumMap<Category, Material> DEFAULT_ICONS = new EnumMap<>(Category.class);

    static {
        DEFAULT_ICONS.put(Category.BLOCKS, Material.GRASS_BLOCK);
        DEFAULT_ICONS.put(Category.ITEMS, Material.APPLE);
        DEFAULT_ICONS.put(Category.ENTITIES, Material.ZOMBIE_HEAD);
        DEFAULT_ICONS.put(Category.BIOMES, Material.OAK_SAPLING);
        DEFAULT_ICONS.put(Category.STRUCTURES, Material.STONE_BRICKS);
        DEFAULT_ICONS.put(Category.EFFECTS, Material.POTION);
        DEFAULT_ICONS.put(Category.ENCHANTMENTS, Material.ENCHANTED_BOOK);
        DEFAULT_ICONS.put(Category.ADVANCEMENTS, Material.KNOWLEDGE_BOOK);
        DEFAULT_ICONS.put(Category.CUSTOM, Material.AMETHYST_SHARD);
    }

    private final ConfigManager config;
    private final AdapterRegistry adapters;

    public CategoryDisplay(ConfigManager config, AdapterRegistry adapters) {
        this.config = config;
        this.adapters = adapters;
    }

    /** The name players see, e.g. "Curios" for {@link Category#CUSTOM}. */
    public String name(Category category) {
        String override = config.getCategoryDisplayName(category);
        if (override != null && !override.isBlank()) return override;
        String n = category.name();
        return n.charAt(0) + n.substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * The icon stack for a category button.
     *
     * <p>A configured icon may be a plain {@link Material} or a custom item key an
     * adapter understands, such as {@code nexo:my_item}. The custom-item form is
     * resolved through the same adapter path entry icons use, so it renders as the
     * real item rather than an approximation. An unresolvable value falls back to
     * the built-in icon rather than breaking the menu.
     */
    public ItemStack icon(Category category) {
        String configured = config.getCategoryIcon(category);
        if (configured != null && !configured.isBlank()) {
            if (configured.indexOf(':') >= 0 && adapters != null) {
                Optional<ItemStack> live = adapters.resolveLiveIcon(configured.toLowerCase(Locale.ROOT));
                if (live.isPresent()) return live.get().clone();
            }
            Material material = Material.matchMaterial(configured);
            if (material != null && material.isItem()) return new ItemStack(material);
        }
        return new ItemStack(DEFAULT_ICONS.getOrDefault(category, Material.PAPER));
    }
}
