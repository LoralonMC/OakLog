package dev.oakheart.oaklog.ui;

import dev.oakheart.message.MessageManager;
import dev.oakheart.oaklog.integration.adapter.AdapterRegistry;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.Entry;
import dev.oakheart.oaklog.model.PlayerProgress;
import dev.oakheart.oaklog.util.DateFormat;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.MusicInstrument;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class IconRenderer {

    private final MessageManager messages;
    private final DateFormat dateFormat;
    private final AdapterRegistry adapterRegistry;

    public IconRenderer(MessageManager messages, DateFormat dateFormat, AdapterRegistry adapterRegistry) {
        this.messages = messages;
        this.dateFormat = dateFormat;
        this.adapterRegistry = adapterRegistry;
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

        TagResolver entryTag = Placeholder.unparsed("entry", entry.displayName());
        TagResolver dateTag = Placeholder.unparsed("date",
                collectedAt != null ? dateFormat.format(collectedAt) : "");

        // Try to use a live icon from the source plugin (preserves real texture,
        // display name, lore for Nexo / ExecutableItems items).
        ItemStack stack = null;
        ItemMeta meta = null;
        boolean usedLiveIcon = false;
        if (adapterRegistry != null) {
            Optional<ItemStack> live = adapterRegistry.resolveLiveIcon(entry.namespacedKey());
            if (live.isPresent()) {
                stack = live.get().clone();
                meta = stack.getItemMeta();
                usedLiveIcon = meta != null;
            }
        }
        if (stack == null) {
            Material material = resolveMaterial(entry.iconMaterial());
            stack = new ItemStack(material);
            meta = stack.getItemMeta();
            if (entry.iconModelData() != null && meta != null) {
                applyCustomModelData(meta, entry.iconModelData());
            }
            // Apply variant components for synthetic vanilla entries that
            // share one Material across many sub-variants (Goat Horns,
            // potions, tipped arrows, enchanted books, potion effects).
            if (meta != null) {
                applyGoatHornVariant(entry, meta);
                applyPotionVariant(entry, meta);
                applyEnchantmentVariant(entry, meta);
                applyEffectVariant(entry, meta);
            }
        }
        if (meta == null) return stack;

        // For live icons keep the source plugin's display name. For stored
        // icons, decide based on category:
        //   - BLOCKS / ITEMS / EFFECTS / ENCHANTMENTS: leave displayName
        //     unset so Minecraft renders the natural component-based name.
        //     For effects + enchantments this means a vanilla-looking potion
        //     (coloured by the effect) and a vanilla-looking enchanted book
        //     (glinting + lore line) respectively.
        //   - BIOMES / STRUCTURES: the icon material is a generic placeholder
        //     so override with entry.displayName() otherwise every biome
        //     renders as "Grass Block" and every structure as "Cobblestone".
        // The messages.yml templates take precedence over both when set.
        if (!usedLiveIcon) {
            String nameKey = collected ? "gui.entry.name-collected" : "gui.entry.name-uncollected";
            Optional<Component> name = messages.parse(nameKey, entryTag, dateTag);
            if (name.isPresent()) {
                meta.displayName(name.get());
            } else if (entry.category() == Category.BIOMES
                    || entry.category() == Category.STRUCTURES) {
                meta.displayName(Component.text(entry.displayName())
                        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            } else if (entry.category() == Category.EFFECTS
                    && !(meta instanceof PotionMeta pm && pm.getBasePotionType() != null)) {
                // Effects without a matching base PotionType render as
                // "Uncraftable Potion" by default. Override with the entry
                // name so Hunger / Glowing / Levitation / etc. are
                // distinguishable.
                meta.displayName(Component.text(entry.displayName())
                        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
        } else {
            // Live-icon path: if the source plugin built the item without
            // setting an explicit name component (some Nexo items omit
            // `itemname:`), the GUI would render Minecraft's default
            // translation — which is the bare material name ("Paper",
            // "Wooden Hoe", etc.) and almost never what the player should
            // see. Use the entry's stored display name as a safety net.
            //
            // displayName() / itemName() returning a Component isn't enough
            // on its own — Paper synthesises a translatable Component for
            // items with no explicit name. The presence checks are the
            // reliable signal.
            boolean hasExplicitName;
            try {
                hasExplicitName = meta.hasDisplayName() || meta.hasItemName();
            } catch (NoSuchMethodError e) {
                hasExplicitName = meta.displayName() != null;
            }
            if (!hasExplicitName) {
                meta.displayName(Component.text(entry.displayName())
                        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
        }

        // Lore: keep any existing lore (from live icon) and append the OakLog
        // status line so collected/uncollected state is visible on every entry.
        List<Component> lore = new ArrayList<>();
        if (usedLiveIcon && meta.hasLore() && meta.lore() != null) {
            for (Component line : meta.lore()) {
                lore.add(line.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
            lore.add(Component.empty());
        }
        String loreKey = collected ? "gui.entry.lore-collected" : "gui.entry.lore-uncollected";
        messages.parse(loreKey, entryTag, dateTag)
                .map(c -> c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE))
                .ifPresent(lore::add);
        meta.lore(lore);

        // Stored/template icons get all hide flags. Live custom icons keep
        // their enchantment glint, attribute lines, and other source-plugin
        // intent — the source plugin's lore already explains what they do.
        if (!usedLiveIcon) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
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
            messages.parse(loreKey, resolvers)
                    .map(c -> c.decorationIfAbsent(net.kyori.adventure.text.format.TextDecoration.ITALIC,
                            net.kyori.adventure.text.format.TextDecoration.State.FALSE))
                    .ifPresent(lore::add);
            meta.lore(lore);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        stack.setItemMeta(meta);
        return stack;
    }

    private static void applyGoatHornVariant(Entry entry, ItemMeta meta) {
        if (!(meta instanceof MusicInstrumentMeta inst)) return;
        if (entry.key() == null || !entry.key().startsWith("goat_horn_")) return;
        String variant = entry.key().substring("goat_horn_".length());
        try {
            Registry<MusicInstrument> reg = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.INSTRUMENT);
            MusicInstrument instrument = reg.get(new NamespacedKey(entry.namespace(),
                    variant + "_goat_horn"));
            if (instrument != null) inst.setInstrument(instrument);
        } catch (Throwable ignored) {}
    }

    private static void applyEffectVariant(Entry entry, ItemMeta meta) {
        if (entry.category() != Category.EFFECTS) return;
        if (!(meta instanceof PotionMeta potionMeta)) return;
        String key = entry.key();
        if (key == null || !key.startsWith("effect_")) return;
        String effectKey = key.substring("effect_".length());
        try {
            Registry<PotionEffectType> effectReg = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.MOB_EFFECT);
            PotionEffectType effectType = effectReg.get(new NamespacedKey(entry.namespace(), effectKey));
            if (effectType == null) return;

            // Prefer setting basePotionType — gives the proper vanilla name
            // ("Potion of Swiftness" for Speed, "Potion of Strength", etc.)
            // and authentic colour.
            org.bukkit.potion.PotionType matched = findBasePotionForEffect(effectType);
            if (matched != null) {
                potionMeta.setBasePotionType(matched);
                return;
            }

            // Effects without a matching base potion (Hunger, Glowing,
            // Levitation, etc.) fall back to a custom-effect potion. We
            // still set the colour so it visually differs from neighbours,
            // and the entry's stored display name fills in for the name.
            potionMeta.addCustomEffect(
                    new PotionEffect(effectType, 600, 0, false, true, true), true);
            potionMeta.setColor(effectType.getColor());
        } catch (Throwable ignored) {}
    }

    private static org.bukkit.potion.PotionType findBasePotionForEffect(PotionEffectType target) {
        try {
            Registry<org.bukkit.potion.PotionType> reg = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.POTION);
            for (org.bukkit.potion.PotionType type : reg) {
                NamespacedKey nk = reg.getKey(type);
                if (nk == null) continue;
                String name = nk.getKey();
                // Skip duration/intensity variants and the no-effect placeholders.
                if (name.startsWith("long_") || name.startsWith("strong_")) continue;
                if (name.equals("awkward") || name.equals("water")
                        || name.equals("mundane") || name.equals("thick")
                        || name.equals("empty")) continue;
                for (PotionEffect e : type.getPotionEffects()) {
                    if (e.getType().equals(target)) return type;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void applyEnchantmentVariant(Entry entry, ItemMeta meta) {
        if (entry.category() != Category.ENCHANTMENTS) return;
        if (!(meta instanceof EnchantmentStorageMeta storage)) return;
        String key = entry.key();
        if (key == null || !key.startsWith("enchantment_")) return;
        String enchKey = key.substring("enchantment_".length());
        try {
            Registry<Enchantment> reg = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.ENCHANTMENT);
            Enchantment ench = reg.get(new NamespacedKey(entry.namespace(), enchKey));
            if (ench != null) storage.addStoredEnchant(ench, 1, true);
        } catch (Throwable ignored) {}
    }

    private static void applyPotionVariant(Entry entry, ItemMeta meta) {
        if (!(meta instanceof PotionMeta potionMeta)) return;
        String key = entry.key();
        if (key == null) return;
        String potionTypeKey = null;
        if (key.startsWith("potion_")) potionTypeKey = key.substring("potion_".length());
        else if (key.startsWith("splash_potion_")) potionTypeKey = key.substring("splash_potion_".length());
        else if (key.startsWith("lingering_potion_")) potionTypeKey = key.substring("lingering_potion_".length());
        else if (key.startsWith("tipped_arrow_")) potionTypeKey = key.substring("tipped_arrow_".length());
        if (potionTypeKey == null) return;
        try {
            Registry<PotionType> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.POTION);
            PotionType type = reg.get(new NamespacedKey(entry.namespace(), potionTypeKey));
            if (type != null) potionMeta.setBasePotionType(type);
        } catch (Throwable ignored) {}
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
            if (m.isAir() || !m.isItem()) return Material.BARRIER;
            return m;
        } catch (IllegalArgumentException e) {
            return Material.BARRIER;
        }
    }
}
