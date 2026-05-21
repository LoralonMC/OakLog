package dev.oakheart.oaklog.enumeration;

import dev.oakheart.oaklog.config.ConfigManager;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.EntryDescriptor;
import dev.oakheart.oaklog.util.EntryKey;
import dev.oakheart.oaklog.variants.VariantResolver;
import dev.oakheart.oaklog.variants.VariantResolvers;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Material;
import org.bukkit.MusicInstrument;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.generator.structure.Structure;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class VanillaEnumerator {

    /**
     * Ageable entity types whose baby form isn't naturally reachable in vanilla —
     * either because the "baby" stage is a separate EntityType (Frog/Tadpole) or
     * because no baby variant spawns at all (Wandering Trader, Trader Llama).
     */
    private static final Set<EntityType> NO_BABY_ENTRY = EnumSet.of(
            EntityType.FROG,
            EntityType.TADPOLE,
            EntityType.WANDERING_TRADER,
            EntityType.TRADER_LLAMA
    );

    private final ConfigManager configManager;
    private final VariantResolvers variantResolvers;

    public VanillaEnumerator(ConfigManager configManager, VariantResolvers variantResolvers) {
        this.configManager = configManager;
        this.variantResolvers = variantResolvers;
    }

    public List<EntryDescriptor> enumerate() {
        List<EntryDescriptor> all = new ArrayList<>();
        List<String> excludedPatterns = configManager.getExcludedItems();

        enumerateMaterials(all, excludedPatterns);
        enumerateEntities(all);
        enumerateBiomes(all);
        enumerateStructures(all);
        enumerateEffects(all);
        enumerateEnchantments(all);

        return all;
    }

    private void enumerateMaterials(List<EntryDescriptor> out, List<String> excludedPatterns) {
        for (Material material : Registry.MATERIAL) {
            if (material.isAir()) continue;
            if (material.isLegacy()) continue;

            String keyName = material.getKey().getKey();
            if (isExcluded(keyName, excludedPatterns)) continue;

            // Skip technical world-only blocks (wall signs, potted plants,
            // wall torches, portal blocks, etc.) — these have isBlock=true
            // but isItem=false and can't actually be collected.
            if (!material.isItem()) continue;

            // Goat Horn: split into one entry per instrument variant.
            if (material == Material.GOAT_HORN) {
                enumerateGoatHornVariants(out);
                continue;
            }
            // Potions / tipped arrows: split by PotionType.
            if (material == Material.POTION || material == Material.SPLASH_POTION
                    || material == Material.LINGERING_POTION
                    || material == Material.TIPPED_ARROW) {
                enumeratePotionVariants(out, material);
                continue;
            }

            boolean isBlock = material.isBlock();
            Category category = isBlock ? Category.BLOCKS : Category.ITEMS;
            String display = EntryKey.displayNameFromKey(keyName);
            out.add(new EntryDescriptor(
                    EntryKey.VANILLA,
                    keyName,
                    category,
                    display,
                    material.name(),
                    null,
                    null
            ));
        }
    }

    private void enumeratePotionVariants(List<EntryDescriptor> out, Material material) {
        Registry<PotionType> potions = RegistryAccess.registryAccess().getRegistry(RegistryKey.POTION);
        String prefix = material.getKey().getKey();           // potion / splash_potion / lingering_potion / tipped_arrow
        String displaySuffix = switch (material) {
            case POTION -> "Potion";
            case SPLASH_POTION -> "Splash Potion";
            case LINGERING_POTION -> "Lingering Potion";
            case TIPPED_ARROW -> "Tipped Arrow";
            default -> EntryKey.displayNameFromKey(prefix);
        };
        for (PotionType type : potions) {
            NamespacedKey nk = potions.getKey(type);
            if (nk == null) continue;
            String typeKey = nk.getKey();
            // The "no effect" baseline that vanilla calls "Uncraftable" / "Empty" — skip.
            if (typeKey.equals("empty") || typeKey.contains("uncraftable")) continue;
            String display = displaySuffix + " of " + EntryKey.displayNameFromKey(typeKey);
            out.add(new EntryDescriptor(
                    EntryKey.VANILLA,
                    prefix + "_" + typeKey,
                    Category.ITEMS,
                    display,
                    material.name(),
                    null,
                    null
            ));
        }
    }

    private void enumerateGoatHornVariants(List<EntryDescriptor> out) {
        Registry<MusicInstrument> instruments =
                RegistryAccess.registryAccess().getRegistry(RegistryKey.INSTRUMENT);
        for (MusicInstrument instrument : instruments) {
            NamespacedKey nk = instruments.getKey(instrument);
            if (nk == null) continue;
            String variant = nk.getKey().replace("_goat_horn", "");
            String displayVariant = EntryKey.displayNameFromKey(variant);
            // Display starts with "Goat Horn" so the eight variants sort
            // together under "G" instead of being spread across the
            // alphabet by their instrument letter.
            out.add(new EntryDescriptor(
                    EntryKey.VANILLA,
                    "goat_horn_" + variant,
                    Category.ITEMS,
                    "Goat Horn - " + displayVariant,
                    "GOAT_HORN",
                    null,
                    null
            ));
        }
    }

    private void enumerateEntities(List<EntryDescriptor> out) {
        for (EntityType type : Registry.ENTITY_TYPE) {
            if (!isCollectableEntity(type)) continue;

            String keyName = type.getKey().getKey();
            String baseDisplay = EntryKey.displayNameFromKey(keyName);
            String baseIcon = pickEntityIcon(type);
            boolean withBabies = hasBabyEntry(type);

            VariantResolver resolver = variantResolvers.get(type);
            if (resolver == null) {
                emitEntityEntries(out, "entity_" + keyName, baseDisplay, baseIcon, withBabies);
                continue;
            }

            if (resolver.hasBaseEntry()) {
                emitEntityEntries(out, "entity_" + keyName, baseDisplay, baseIcon, withBabies);
            }

            for (String variantKey : resolver.variantKeys()) {
                String suffix = resolver.displayName(variantKey);
                String iconOverride = resolver.iconMaterial(variantKey);
                String key = "entity_" + keyName + "_" + variantKey;
                String display = suffix + " " + baseDisplay;
                String icon = iconOverride != null ? iconOverride : baseIcon;
                emitEntityEntries(out, key, display, icon, withBabies);
            }
        }
    }

    /**
     * Emits the adult entry — and, if the entity is Ageable with a reachable baby
     * form, the paired {@code _baby} entry with a "Baby " display prefix.
     */
    private static void emitEntityEntries(List<EntryDescriptor> out, String key,
                                          String display, String icon, boolean withBabies) {
        out.add(new EntryDescriptor(EntryKey.VANILLA, key, Category.ENTITIES,
                display, icon, null, null));
        if (withBabies) {
            out.add(new EntryDescriptor(EntryKey.VANILLA, key + "_baby", Category.ENTITIES,
                    "Baby " + display, icon, null, null));
        }
    }

    private static boolean hasBabyEntry(EntityType type) {
        if (NO_BABY_ENTRY.contains(type)) return false;
        Class<?> cls = type.getEntityClass();
        return cls != null && Ageable.class.isAssignableFrom(cls);
    }

    private void enumerateBiomes(List<EntryDescriptor> out) {
        Registry<Biome> biomes = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        for (Biome biome : biomes) {
            NamespacedKey nk = biomes.getKey(biome);
            if (nk == null) continue;
            String display = EntryKey.displayNameFromKey(nk.getKey());

            out.add(new EntryDescriptor(
                    nk.getNamespace(),
                    "biome_" + nk.getKey(),
                    Category.BIOMES,
                    display,
                    "GRASS_BLOCK",
                    null,
                    null
            ));
        }
    }

    private void enumerateStructures(List<EntryDescriptor> out) {
        Registry<Structure> structures = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
        for (Structure structure : structures) {
            NamespacedKey nk = structures.getKey(structure);
            if (nk == null) continue;
            String display = EntryKey.displayNameFromKey(nk.getKey());

            out.add(new EntryDescriptor(
                    nk.getNamespace(),
                    "structure_" + nk.getKey(),
                    Category.STRUCTURES,
                    display,
                    "COBBLESTONE",
                    null,
                    null
            ));
        }
    }

    private void enumerateEffects(List<EntryDescriptor> out) {
        Registry<PotionEffectType> effects =
                RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT);
        for (PotionEffectType effect : effects) {
            NamespacedKey nk = effects.getKey(effect);
            if (nk == null) continue;
            String display = EntryKey.displayNameFromKey(nk.getKey());

            out.add(new EntryDescriptor(
                    nk.getNamespace(),
                    "effect_" + nk.getKey(),
                    Category.EFFECTS,
                    display,
                    "POTION",
                    null,
                    null
            ));
        }
    }

    private void enumerateEnchantments(List<EntryDescriptor> out) {
        Registry<Enchantment> enchants =
                RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT);
        for (Enchantment ench : enchants) {
            NamespacedKey nk = enchants.getKey(ench);
            if (nk == null) continue;
            String display = EntryKey.displayNameFromKey(nk.getKey());

            out.add(new EntryDescriptor(
                    nk.getNamespace(),
                    "enchantment_" + nk.getKey(),
                    Category.ENCHANTMENTS,
                    display,
                    "ENCHANTED_BOOK",
                    null,
                    null
            ));
        }
    }

    private static boolean isExcluded(String materialKey, List<String> patterns) {
        String lower = materialKey.toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (EntryKey.globMatches(pattern.toLowerCase(Locale.ROOT), lower)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCollectableEntity(EntityType type) {
        if (type == EntityType.PLAYER) return false;
        if (type == EntityType.ARMOR_STAND) return false;
        if (type == EntityType.PAINTING) return true;

        Class<?> cls = type.getEntityClass();
        return cls != null && LivingEntity.class.isAssignableFrom(cls);
    }

    private static String pickEntityIcon(EntityType type) {
        // Try to use the matching spawn egg material; fall back to a sensible default.
        String guess = type.name() + "_SPAWN_EGG";
        try {
            Material.valueOf(guess);
            return guess;
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        if (type == EntityType.PAINTING) return "PAINTING";
        return "BARRIER";
    }
}
