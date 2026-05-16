package dev.oakheart.oaklog.variants;

import dev.oakheart.oaklog.util.EntryKey;
import org.bukkit.entity.Entity;

import java.util.List;

/**
 * Splits a variant-bearing entity type into multiple collection-log entries.
 * <p>
 * Each implementation answers two questions:
 * <ol>
 *   <li>At registry-build time: what are all the variant keys?</li>
 *   <li>At detection time: what variant is this specific entity?</li>
 * </ol>
 * The final entry key for an entity is {@code <baseKey>_<variantKey>} (e.g.
 * {@code sheep_white}, {@code villager_plains_farmer}).
 */
public interface VariantResolver {

    /**
     * Returns all possible variant keys for this entity type, lowercase, snake_case.
     * Order is the display order in the GUI.
     */
    List<String> variantKeys();

    /**
     * Returns the variant key for the given entity instance, or {@code null}
     * if the entity has no variant information (treat as base type).
     */
    String resolve(Entity entity);

    /**
     * Optional display-name suffix override. Default is a title-cased version of
     * the variant key (e.g. "plains_farmer" -> "Plains Farmer").
     */
    default String displayName(String variantKey) {
        return EntryKey.displayNameFromKey(variantKey);
    }

    /**
     * Optional icon material override for the given variant. Return {@code null}
     * to use the base entity's icon.
     */
    default String iconMaterial(String variantKey) {
        return null;
    }
}
