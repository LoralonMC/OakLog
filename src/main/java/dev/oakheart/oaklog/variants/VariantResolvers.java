package dev.oakheart.oaklog.variants;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Art;
import org.bukkit.DyeColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Llama;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.ZombieNautilus;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Registry mapping {@link EntityType} -> {@link VariantResolver}.
 * Resolvers that need their own file (Villager, TropicalFish) live alongside;
 * simpler enum-backed resolvers are nested static classes here.
 */
public final class VariantResolvers {

    private final Map<EntityType, VariantResolver> resolvers = new EnumMap<>(EntityType.class);

    public VariantResolvers() {
        resolvers.put(EntityType.SHEEP, new SheepVariant());
        resolvers.put(EntityType.COW, new CowVariant());
        resolvers.put(EntityType.PANDA, new PandaVariant());
        resolvers.put(EntityType.WOLF, new WolfVariant());
        resolvers.put(EntityType.FROG, new FrogVariant());
        resolvers.put(EntityType.AXOLOTL, new AxolotlVariant());
        resolvers.put(EntityType.CAT, new CatVariant());
        resolvers.put(EntityType.LLAMA, new LlamaVariant());
        resolvers.put(EntityType.TRADER_LLAMA, new LlamaVariant());
        resolvers.put(EntityType.PARROT, new ParrotVariant());
        resolvers.put(EntityType.HORSE, new HorseVariant());
        resolvers.put(EntityType.RABBIT, new RabbitVariant());
        resolvers.put(EntityType.MOOSHROOM, new MooshroomVariant());
        resolvers.put(EntityType.FOX, new FoxVariant());
        resolvers.put(EntityType.TROPICAL_FISH, new TropicalFishVariant());
        resolvers.put(EntityType.PAINTING, new PaintingVariant());
        resolvers.put(EntityType.VILLAGER, new VillagerVariant());
        resolvers.put(EntityType.ZOMBIE_NAUTILUS, new ZombieNautilusVariant());
    }

    public VariantResolver get(EntityType type) {
        return resolvers.get(type);
    }

    public boolean hasResolver(EntityType type) {
        return resolvers.containsKey(type);
    }

    // ============================================================
    // Resolver implementations
    // ============================================================

    private static final class SheepVariant implements VariantResolver {
        @Override
        public List<String> variantKeys() {
            List<String> out = new ArrayList<>();
            for (DyeColor color : DyeColor.values()) {
                out.add(color.name().toLowerCase(Locale.ROOT));
            }
            return out;
        }

        @Override
        public String resolve(Entity entity) {
            if (!(entity instanceof Sheep sheep)) return null;
            DyeColor color = sheep.getColor();
            return color == null ? null : color.name().toLowerCase(Locale.ROOT);
        }

        @Override
        public String iconMaterial(String variantKey) {
            return variantKey.toUpperCase(Locale.ROOT) + "_WOOL";
        }
    }

    private static final class CowVariant implements VariantResolver {
        @Override
        public List<String> variantKeys() {
            Registry<Cow.Variant> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.COW_VARIANT);
            List<String> out = new ArrayList<>();
            for (Cow.Variant v : reg) {
                NamespacedKey nk = reg.getKey(v);
                if (nk != null) out.add(nk.getKey());
            }
            return out;
        }

        @Override
        public String resolve(Entity entity) {
            if (!(entity instanceof Cow cow)) return null;
            Registry<Cow.Variant> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.COW_VARIANT);
            NamespacedKey nk = reg.getKey(cow.getVariant());
            return nk == null ? null : nk.getKey();
        }
    }

    private static final class PandaVariant implements VariantResolver {
        @Override
        public List<String> variantKeys() {
            List<String> out = new ArrayList<>();
            for (Panda.Gene gene : Panda.Gene.values()) {
                out.add(gene.name().toLowerCase(Locale.ROOT));
            }
            return out;
        }

        @Override
        public String resolve(Entity entity) {
            if (!(entity instanceof Panda panda)) return null;
            Panda.Gene gene = panda.getMainGene();
            return gene == null ? null : gene.name().toLowerCase(Locale.ROOT);
        }
    }

    private static final class WolfVariant implements VariantResolver {
        @Override
        public List<String> variantKeys() {
            Registry<Wolf.Variant> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.WOLF_VARIANT);
            List<String> out = new ArrayList<>();
            for (Wolf.Variant v : reg) {
                NamespacedKey nk = reg.getKey(v);
                if (nk != null) out.add(nk.getKey());
            }
            return out;
        }

        @Override
        public String resolve(Entity entity) {
            if (!(entity instanceof Wolf wolf)) return null;
            Registry<Wolf.Variant> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.WOLF_VARIANT);
            NamespacedKey nk = reg.getKey(wolf.getVariant());
            return nk == null ? null : nk.getKey();
        }
    }

    private static final class FrogVariant implements VariantResolver {
        @Override
        public List<String> variantKeys() {
            Registry<Frog.Variant> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.FROG_VARIANT);
            List<String> out = new ArrayList<>();
            for (Frog.Variant v : reg) {
                NamespacedKey nk = reg.getKey(v);
                if (nk != null) out.add(nk.getKey());
            }
            return out;
        }

        @Override
        public String resolve(Entity entity) {
            if (!(entity instanceof Frog frog)) return null;
            Registry<Frog.Variant> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.FROG_VARIANT);
            NamespacedKey nk = reg.getKey(frog.getVariant());
            return nk == null ? null : nk.getKey();
        }
    }

    private static final class AxolotlVariant implements VariantResolver {
        @Override
        public List<String> variantKeys() {
            List<String> out = new ArrayList<>();
            for (Axolotl.Variant v : Axolotl.Variant.values()) {
                out.add(v.name().toLowerCase(Locale.ROOT));
            }
            return out;
        }

        @Override
        public String resolve(Entity entity) {
            if (!(entity instanceof Axolotl axolotl)) return null;
            Axolotl.Variant v = axolotl.getVariant();
            return v == null ? null : v.name().toLowerCase(Locale.ROOT);
        }
    }

    private static final class CatVariant implements VariantResolver {
        @Override
        public List<String> variantKeys() {
            Registry<Cat.Type> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.CAT_VARIANT);
            List<String> out = new ArrayList<>();
            for (Cat.Type t : reg) {
                NamespacedKey nk = reg.getKey(t);
                if (nk != null) out.add(nk.getKey());
            }
            return out;
        }

        @Override
        public String resolve(Entity entity) {
            if (!(entity instanceof Cat cat)) return null;
            Registry<Cat.Type> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.CAT_VARIANT);
            NamespacedKey nk = reg.getKey(cat.getCatType());
            return nk == null ? null : nk.getKey();
        }
    }

    private static final class LlamaVariant implements VariantResolver {
        @Override
        public List<String> variantKeys() {
            List<String> out = new ArrayList<>();
            for (Llama.Color c : Llama.Color.values()) {
                out.add(c.name().toLowerCase(Locale.ROOT));
            }
            return out;
        }

        @Override
        public String resolve(Entity entity) {
            if (!(entity instanceof Llama llama)) return null;
            Llama.Color c = llama.getColor();
            return c == null ? null : c.name().toLowerCase(Locale.ROOT);
        }
    }

    private static final class ParrotVariant implements VariantResolver {
        @Override
        public List<String> variantKeys() {
            List<String> out = new ArrayList<>();
            for (Parrot.Variant v : Parrot.Variant.values()) {
                out.add(v.name().toLowerCase(Locale.ROOT));
            }
            return out;
        }

        @Override
        public String resolve(Entity entity) {
            if (!(entity instanceof Parrot parrot)) return null;
            Parrot.Variant v = parrot.getVariant();
            return v == null ? null : v.name().toLowerCase(Locale.ROOT);
        }
    }

    private static final class HorseVariant implements VariantResolver {
        @Override
        public List<String> variantKeys() {
            List<String> out = new ArrayList<>();
            for (Horse.Color color : Horse.Color.values()) {
                for (Horse.Style style : Horse.Style.values()) {
                    out.add(color.name().toLowerCase(Locale.ROOT) + "_" +
                            style.name().toLowerCase(Locale.ROOT));
                }
            }
            return out;
        }

        @Override
        public String resolve(Entity entity) {
            if (!(entity instanceof Horse horse)) return null;
            Horse.Color color = horse.getColor();
            Horse.Style style = horse.getStyle();
            if (color == null || style == null) return null;
            return color.name().toLowerCase(Locale.ROOT) + "_" + style.name().toLowerCase(Locale.ROOT);
        }
    }

    private static final class RabbitVariant implements VariantResolver {
        @Override
        public List<String> variantKeys() {
            List<String> out = new ArrayList<>();
            for (Rabbit.Type t : Rabbit.Type.values()) {
                out.add(t.name().toLowerCase(Locale.ROOT));
            }
            return out;
        }

        @Override
        public String resolve(Entity entity) {
            if (!(entity instanceof Rabbit rabbit)) return null;
            Rabbit.Type t = rabbit.getRabbitType();
            return t == null ? null : t.name().toLowerCase(Locale.ROOT);
        }
    }

    private static final class MooshroomVariant implements VariantResolver {
        @Override
        public List<String> variantKeys() {
            List<String> out = new ArrayList<>();
            for (MushroomCow.Variant v : MushroomCow.Variant.values()) {
                out.add(v.name().toLowerCase(Locale.ROOT));
            }
            return out;
        }

        @Override
        public String resolve(Entity entity) {
            if (!(entity instanceof MushroomCow cow)) return null;
            MushroomCow.Variant v = cow.getVariant();
            return v == null ? null : v.name().toLowerCase(Locale.ROOT);
        }
    }

    private static final class FoxVariant implements VariantResolver {
        @Override
        public List<String> variantKeys() {
            List<String> out = new ArrayList<>();
            for (Fox.Type t : Fox.Type.values()) {
                out.add(t.name().toLowerCase(Locale.ROOT));
            }
            return out;
        }

        @Override
        public String resolve(Entity entity) {
            if (!(entity instanceof Fox fox)) return null;
            Fox.Type t = fox.getFoxType();
            return t == null ? null : t.name().toLowerCase(Locale.ROOT);
        }
    }

    private static final class ZombieNautilusVariant implements VariantResolver {
        @Override
        public List<String> variantKeys() {
            Registry<ZombieNautilus.Variant> reg = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.ZOMBIE_NAUTILUS_VARIANT);
            List<String> out = new ArrayList<>();
            for (ZombieNautilus.Variant v : reg) {
                NamespacedKey nk = reg.getKey(v);
                if (nk != null) out.add(nk.getKey());
            }
            return out;
        }

        @Override
        public String resolve(Entity entity) {
            if (!(entity instanceof ZombieNautilus z)) return null;
            Registry<ZombieNautilus.Variant> reg = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.ZOMBIE_NAUTILUS_VARIANT);
            NamespacedKey nk = reg.getKey(z.getVariant());
            return nk == null ? null : nk.getKey();
        }
    }

    private static final class PaintingVariant implements VariantResolver {
        @Override
        public List<String> variantKeys() {
            Registry<Art> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.PAINTING_VARIANT);
            List<String> out = new ArrayList<>();
            for (Art art : reg) {
                NamespacedKey nk = reg.getKey(art);
                if (nk != null) out.add(nk.getKey());
            }
            return out;
        }

        @Override
        public String resolve(Entity entity) {
            if (!(entity instanceof Painting painting)) return null;
            Registry<Art> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.PAINTING_VARIANT);
            NamespacedKey nk = reg.getKey(painting.getArt());
            return nk == null ? null : nk.getKey();
        }

        @Override
        public String iconMaterial(String variantKey) {
            return "PAINTING";
        }
    }
}
