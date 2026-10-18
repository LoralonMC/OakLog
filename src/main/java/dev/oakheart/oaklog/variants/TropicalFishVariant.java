package dev.oakheart.oaklog.variants;

import org.bukkit.DyeColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TropicalFish;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The 22 vanilla named tropical fish presets. Each is a unique combination of
 * pattern, body color, and pattern color, plus a memorable name.
 */
public final class TropicalFishVariant implements VariantResolver {

    private static final Map<String, Preset> PRESETS = new LinkedHashMap<>();
    private static final Map<String, String> COMBO_TO_KEY = new LinkedHashMap<>();

    static {
        // Order and values copied from TropicalFish.COMMON_VARIANTS in the 26.1.2 server jar;
        // index N is lang key entity.minecraft.tropical_fish.predefined.N. Until 1.3.2, 12 of
        // these rows were wrong (ticket bug-52): ten fish could never be logged, and Black Tang
        // and Queen Angelfish were credited as Blue Tang and Yellowtail Parrotfish. Re-derive
        // from the jar rather than a wiki if this ever needs touching.
        register("anemone", TropicalFish.Pattern.STRIPEY, DyeColor.ORANGE, DyeColor.GRAY);
        register("black_tang", TropicalFish.Pattern.FLOPPER, DyeColor.GRAY, DyeColor.GRAY);
        register("blue_tang", TropicalFish.Pattern.FLOPPER, DyeColor.GRAY, DyeColor.BLUE);
        register("butterflyfish", TropicalFish.Pattern.CLAYFISH, DyeColor.WHITE, DyeColor.GRAY);
        register("cichlid", TropicalFish.Pattern.SUNSTREAK, DyeColor.BLUE, DyeColor.GRAY);
        register("clownfish", TropicalFish.Pattern.KOB, DyeColor.ORANGE, DyeColor.WHITE);
        register("cotton_candy_betta", TropicalFish.Pattern.SPOTTY, DyeColor.PINK, DyeColor.LIGHT_BLUE);
        register("dottyback", TropicalFish.Pattern.BLOCKFISH, DyeColor.PURPLE, DyeColor.YELLOW);
        register("emperor_red_snapper", TropicalFish.Pattern.CLAYFISH, DyeColor.WHITE, DyeColor.RED);
        register("goatfish", TropicalFish.Pattern.SPOTTY, DyeColor.WHITE, DyeColor.YELLOW);
        register("moorish_idol", TropicalFish.Pattern.GLITTER, DyeColor.WHITE, DyeColor.GRAY);
        register("ornate_butterflyfish", TropicalFish.Pattern.CLAYFISH, DyeColor.WHITE, DyeColor.ORANGE);
        register("parrotfish", TropicalFish.Pattern.DASHER, DyeColor.CYAN, DyeColor.PINK);
        register("queen_angelfish", TropicalFish.Pattern.BRINELY, DyeColor.LIME, DyeColor.LIGHT_BLUE);
        register("red_cichlid", TropicalFish.Pattern.BETTY, DyeColor.RED, DyeColor.WHITE);
        register("red_lipped_blenny", TropicalFish.Pattern.SNOOPER, DyeColor.GRAY, DyeColor.RED);
        register("red_snapper", TropicalFish.Pattern.BLOCKFISH, DyeColor.RED, DyeColor.WHITE);
        register("threadfin", TropicalFish.Pattern.FLOPPER, DyeColor.WHITE, DyeColor.YELLOW);
        register("tomato_clownfish", TropicalFish.Pattern.KOB, DyeColor.RED, DyeColor.WHITE);
        register("triggerfish", TropicalFish.Pattern.SUNSTREAK, DyeColor.GRAY, DyeColor.WHITE);
        register("yellowtail_parrotfish", TropicalFish.Pattern.DASHER, DyeColor.CYAN, DyeColor.YELLOW);
        register("yellow_tang", TropicalFish.Pattern.FLOPPER, DyeColor.YELLOW, DyeColor.YELLOW);
    }

    private static void register(String name, TropicalFish.Pattern pattern, DyeColor body, DyeColor patternColor) {
        Preset preset = new Preset(name, pattern, body, patternColor);
        PRESETS.put(name, preset);
        COMBO_TO_KEY.put(comboKey(pattern, body, patternColor), name);
    }

    private static String comboKey(TropicalFish.Pattern pattern, DyeColor body, DyeColor patternColor) {
        return pattern.name() + "|" + body.name() + "|" + patternColor.name();
    }

    @Override
    public List<String> variantKeys() {
        return new ArrayList<>(PRESETS.keySet());
    }

    @Override
    public String resolve(Entity entity) {
        if (!(entity instanceof TropicalFish fish)) return null;
        String key = comboKey(fish.getPattern(), fish.getBodyColor(), fish.getPatternColor());
        return COMBO_TO_KEY.get(key);
    }

    @Override
    public String displayName(String variantKey) {
        String[] parts = variantKey.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            String p = parts[i];
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private record Preset(String name, TropicalFish.Pattern pattern, DyeColor body, DyeColor patternColor) {}
}
