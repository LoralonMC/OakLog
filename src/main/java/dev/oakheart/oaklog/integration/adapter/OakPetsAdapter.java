package dev.oakheart.oaklog.integration.adapter;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.EntryDescriptor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
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
 * Direct integration with the user's OakPets plugin via its
 * {@code OakPetsAPI} and {@code PetVoucherBuilder}. All access is
 * reflection-based so OakLog can compile without OakPets on the classpath.
 */
public class OakPetsAdapter implements EntryAdapter {

    private static final String NAMESPACE = "oakpets";
    private static final NamespacedKey PDC_KEY =
            new NamespacedKey(NAMESPACE, "pet_voucher_id");

    private final OakLog plugin;
    private final Logger logger;

    private Plugin host;
    private Object api;
    private Method getAllPetsMethod;
    private Method petInfoIdMethod;
    private Method petInfoDisplayNameMethod;
    private Constructor<?> voucherCtor;
    private Method voucherBuildMethod;

    private final Map<String, ItemStack> iconCache = new ConcurrentHashMap<>();

    public OakPetsAdapter(OakLog plugin) {
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
            Class<?> apiClass = Class.forName("dev.oakheart.oakpets.api.OakPetsAPI", false, cl);
            api = apiClass.getMethod("getInstance").invoke(null);
            if (api == null) return false;
            getAllPetsMethod = apiClass.getMethod("getAllPets");

            Class<?> petInfoClass = Class.forName("dev.oakheart.oakpets.api.PetInfo", false, cl);
            petInfoIdMethod = petInfoClass.getMethod("id");
            petInfoDisplayNameMethod = petInfoClass.getMethod("displayName");

            Class<?> builderClass = Class.forName("dev.oakheart.oakpets.api.PetVoucherBuilder", false, cl);
            voucherCtor = builderClass.getConstructor(String.class);
            voucherBuildMethod = builderClass.getMethod("build");
            return true;
        } catch (Throwable t) {
            logger.log(Level.FINE, "OakPets adapter unavailable: " + t.getClass().getSimpleName());
            return false;
        }
    }

    @Override
    public List<EntryDescriptor> enumerate() {
        if (api == null) return List.of();
        try {
            Object result = getAllPetsMethod.invoke(api);
            if (!(result instanceof Collection<?> coll)) return List.of();

            List<EntryDescriptor> out = new ArrayList<>();
            for (Object petInfo : coll) {
                String petId = ((String) petInfoIdMethod.invoke(petInfo)).toLowerCase(Locale.ROOT);
                String displayName = (String) petInfoDisplayNameMethod.invoke(petInfo);
                if (displayName == null || displayName.isBlank()) displayName = petId;

                // Pre-build the voucher so the icon is ready when the GUI opens.
                ItemStack voucher = buildVoucher(petId);
                String iconMaterial = voucher != null
                        ? voucher.getType().name()
                        : "PAPER";
                if (voucher != null) {
                    iconCache.put(petId, voucher);
                }

                out.add(new EntryDescriptor(
                        NAMESPACE,
                        petId,
                        Category.ITEMS,
                        displayName,
                        iconMaterial,
                        null,
                        null
                ));
            }
            logger.info("OakPets adapter: enumerated " + out.size() + " pet vouchers");
            return out;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "OakPets enumerate failed", t);
            return List.of();
        }
    }

    @Override
    public String detect(ItemStack stack) {
        try {
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) return null;
            String petId = meta.getPersistentDataContainer().get(PDC_KEY, PersistentDataType.STRING);
            if (petId == null || petId.isBlank()) return null;
            return NAMESPACE + ":" + petId.toLowerCase(Locale.ROOT);
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
        if (cached != null) return Optional.of(cached.clone());

        ItemStack built = buildVoucher(id);
        if (built != null) iconCache.put(id, built);
        return Optional.ofNullable(built);
    }

    private ItemStack buildVoucher(String petId) {
        if (voucherCtor == null || voucherBuildMethod == null) return null;
        try {
            Object builder = voucherCtor.newInstance(petId);
            Object built = voucherBuildMethod.invoke(builder);
            return built instanceof ItemStack stack ? stack : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private Plugin findHostPlugin() {
        for (Plugin p : plugin.getServer().getPluginManager().getPlugins()) {
            if (p.getClass().getName().startsWith("dev.oakheart.oakpets")) return p;
        }
        return null;
    }
}
