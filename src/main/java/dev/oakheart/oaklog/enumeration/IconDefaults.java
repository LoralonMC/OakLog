package dev.oakheart.oaklog.enumeration;

import java.util.Map;

/**
 * Per-biome and per-structure default icon materials. These map the path
 * portion of a vanilla Biome / Structure NamespacedKey to a Bukkit Material
 * name string suitable for {@link org.bukkit.Material#valueOf(String)}.
 *
 * Lookups apply only when the entry's namespace is {@code minecraft}; modded
 * biomes / structures fall back to the generic GRASS_BLOCK / COBBLESTONE
 * defaults provided by the enumerator.
 */
public final class IconDefaults {

    private IconDefaults() {}

    private static final String FALLBACK_BIOME = "GRASS_BLOCK";
    private static final String FALLBACK_STRUCTURE = "COBBLESTONE";

    public static String biomeIcon(String namespace, String key) {
        if (!"minecraft".equals(namespace)) return FALLBACK_BIOME;
        return BIOMES.getOrDefault(key, FALLBACK_BIOME);
    }

    public static String structureIcon(String namespace, String key) {
        if (!"minecraft".equals(namespace)) return FALLBACK_STRUCTURE;
        return STRUCTURES.getOrDefault(key, FALLBACK_STRUCTURE);
    }

    private static final Map<String, String> BIOMES = Map.ofEntries(
            // Overworld surface
            Map.entry("plains", "GRASS_BLOCK"),
            Map.entry("sunflower_plains", "SUNFLOWER"),
            Map.entry("forest", "OAK_SAPLING"),
            Map.entry("flower_forest", "ALLIUM"),
            Map.entry("birch_forest", "BIRCH_SAPLING"),
            Map.entry("old_growth_birch_forest", "BIRCH_SAPLING"),
            Map.entry("dark_forest", "DARK_OAK_SAPLING"),
            Map.entry("pale_garden", "PALE_OAK_SAPLING"),
            Map.entry("taiga", "SPRUCE_SAPLING"),
            Map.entry("snowy_taiga", "SNOW"),
            Map.entry("old_growth_pine_taiga", "PODZOL"),
            Map.entry("old_growth_spruce_taiga", "SPRUCE_LEAVES"),
            Map.entry("savanna", "ACACIA_SAPLING"),
            Map.entry("savanna_plateau", "ACACIA_SAPLING"),
            Map.entry("windswept_savanna", "ACACIA_SAPLING"),
            Map.entry("jungle", "JUNGLE_SAPLING"),
            Map.entry("sparse_jungle", "JUNGLE_SAPLING"),
            Map.entry("bamboo_jungle", "BAMBOO"),

            // Hot / arid
            Map.entry("desert", "SAND"),
            Map.entry("badlands", "RED_SAND"),
            Map.entry("eroded_badlands", "RED_SANDSTONE"),
            Map.entry("wooded_badlands", "TERRACOTTA"),

            // Wetlands
            Map.entry("swamp", "LILY_PAD"),
            Map.entry("mangrove_swamp", "MANGROVE_ROOTS"),

            // Misc surface
            Map.entry("cherry_grove", "CHERRY_SAPLING"),
            Map.entry("dappled_forest", "POPLAR_SAPLING"),
            Map.entry("meadow", "WILDFLOWERS"),
            Map.entry("grove", "SNOW"),

            // Snow / mountain
            Map.entry("snowy_slopes", "POWDER_SNOW"),
            Map.entry("snowy_plains", "SNOW"),
            Map.entry("snowy_beach", "SNOW"),
            Map.entry("ice_spikes", "BLUE_ICE"),
            Map.entry("frozen_peaks", "PACKED_ICE"),
            Map.entry("jagged_peaks", "SNOW_BLOCK"),
            Map.entry("stony_peaks", "STONE"),
            Map.entry("windswept_hills", "ANDESITE"),
            Map.entry("windswept_gravelly_hills", "GRAVEL"),
            Map.entry("windswept_forest", "SPRUCE_LEAVES"),
            Map.entry("stony_shore", "STONE"),
            Map.entry("beach", "SAND"),

            // Water
            Map.entry("river", "WATER_BUCKET"),
            Map.entry("frozen_river", "ICE"),
            Map.entry("ocean", "SEAGRASS"),
            Map.entry("deep_ocean", "KELP"),
            Map.entry("warm_ocean", "BUBBLE_CORAL_BLOCK"),
            Map.entry("lukewarm_ocean", "SEAGRASS"),
            Map.entry("deep_lukewarm_ocean", "KELP"),
            Map.entry("cold_ocean", "SEAGRASS"),
            Map.entry("deep_cold_ocean", "KELP"),
            Map.entry("frozen_ocean", "PACKED_ICE"),
            Map.entry("deep_frozen_ocean", "BLUE_ICE"),

            // Caves
            Map.entry("mushroom_fields", "RED_MUSHROOM_BLOCK"),
            Map.entry("dripstone_caves", "DRIPSTONE_BLOCK"),
            Map.entry("lush_caves", "FLOWERING_AZALEA"),
            Map.entry("deep_dark", "SCULK"),
            Map.entry("sulfur_caves", "SULFUR"),

            // Nether
            Map.entry("nether_wastes", "NETHERRACK"),
            Map.entry("crimson_forest", "CRIMSON_NYLIUM"),
            Map.entry("warped_forest", "WARPED_NYLIUM"),
            Map.entry("soul_sand_valley", "SOUL_SAND"),
            Map.entry("basalt_deltas", "BASALT"),

            // End
            Map.entry("the_end", "END_STONE"),
            Map.entry("end_highlands", "PURPUR_BLOCK"),
            Map.entry("end_midlands", "PURPUR_PILLAR"),
            Map.entry("end_barrens", "CHORUS_FLOWER"),
            Map.entry("small_end_islands", "END_STONE"),
            Map.entry("the_void", "BARRIER")
    );

    private static final Map<String, String> STRUCTURES = Map.ofEntries(
            Map.entry("ancient_city", "SCULK_SHRIEKER"),
            Map.entry("bastion_remnant", "CHISELED_POLISHED_BLACKSTONE"),
            Map.entry("buried_treasure", "CHEST"),
            Map.entry("desert_pyramid", "CHISELED_SANDSTONE"),
            Map.entry("end_city", "PURPUR_PILLAR"),
            Map.entry("fortress", "NETHER_BRICKS"),
            Map.entry("igloo", "SNOW_BLOCK"),
            Map.entry("jungle_pyramid", "MOSSY_COBBLESTONE"),
            Map.entry("mansion", "DARK_OAK_PLANKS"),
            Map.entry("mineshaft", "OAK_FENCE"),
            Map.entry("mineshaft_mesa", "DARK_OAK_FENCE"),
            Map.entry("monument", "PRISMARINE_BRICKS"),
            Map.entry("nether_fossil", "BONE_BLOCK"),
            Map.entry("ocean_ruin_cold", "STONE_BRICKS"),
            Map.entry("ocean_ruin_warm", "CUT_SANDSTONE"),
            Map.entry("pillager_outpost", "DARK_OAK_LOG"),
            Map.entry("ruined_portal", "CRYING_OBSIDIAN"),
            Map.entry("ruined_portal_desert", "CRYING_OBSIDIAN"),
            Map.entry("ruined_portal_jungle", "CRYING_OBSIDIAN"),
            Map.entry("ruined_portal_mountain", "CRYING_OBSIDIAN"),
            Map.entry("ruined_portal_nether", "CRYING_OBSIDIAN"),
            Map.entry("ruined_portal_ocean", "CRYING_OBSIDIAN"),
            Map.entry("ruined_portal_swamp", "CRYING_OBSIDIAN"),
            Map.entry("shipwreck", "SPRUCE_PLANKS"),
            Map.entry("shipwreck_beached", "OAK_PLANKS"),
            Map.entry("stronghold", "END_PORTAL_FRAME"),
            Map.entry("swamp_hut", "CAULDRON"),
            Map.entry("trail_ruins", "MUD_BRICKS"),
            Map.entry("trial_chambers", "COPPER_BULB"),
            Map.entry("village_desert", "SMOOTH_SANDSTONE"),
            Map.entry("village_plains", "HAY_BLOCK"),
            Map.entry("village_savanna", "ACACIA_PLANKS"),
            Map.entry("village_snowy", "STRIPPED_SPRUCE_LOG"),
            Map.entry("village_taiga", "SPRUCE_LOG")
    );
}
