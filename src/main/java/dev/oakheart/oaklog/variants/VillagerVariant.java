package dev.oakheart.oaklog.variants;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;

import java.util.ArrayList;
import java.util.List;

/**
 * Villager variants are the Cartesian product of biome type and profession.
 * Both registries are datapack-aware; entries are computed at runtime.
 */
public final class VillagerVariant implements VariantResolver {

    @Override
    public List<String> variantKeys() {
        Registry<Villager.Type> types = RegistryAccess.registryAccess().getRegistry(RegistryKey.VILLAGER_TYPE);
        Registry<Villager.Profession> profs = RegistryAccess.registryAccess().getRegistry(RegistryKey.VILLAGER_PROFESSION);

        List<String> keys = new ArrayList<>();
        for (Villager.Type type : types) {
            NamespacedKey tk = types.getKey(type);
            if (tk == null) continue;
            for (Villager.Profession profession : profs) {
                NamespacedKey pk = profs.getKey(profession);
                if (pk == null) continue;
                keys.add(tk.getKey() + "_" + pk.getKey());
            }
        }
        return keys;
    }

    @Override
    public String resolve(Entity entity) {
        if (!(entity instanceof Villager villager)) return null;
        Registry<Villager.Type> types = RegistryAccess.registryAccess().getRegistry(RegistryKey.VILLAGER_TYPE);
        Registry<Villager.Profession> profs = RegistryAccess.registryAccess().getRegistry(RegistryKey.VILLAGER_PROFESSION);
        NamespacedKey tk = types.getKey(villager.getVillagerType());
        NamespacedKey pk = profs.getKey(villager.getProfession());
        if (tk == null || pk == null) return null;
        return tk.getKey() + "_" + pk.getKey();
    }
}
