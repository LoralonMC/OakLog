package dev.oakheart.oaklog.detection;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.config.ConfigManager;
import dev.oakheart.oaklog.model.PlayerProgress;
import dev.oakheart.oaklog.progress.ProgressManager;
import dev.oakheart.oaklog.registry.EntryRegistry;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Detects structures by testing whether the player is standing inside a generated
 * structure's bounding box.
 *
 * <p>Sampling happens both on movement-ish events (chunk cross, join, teleport, world
 * change) for instant credit, and on a repeating poll. The poll is what makes small
 * structures collectable at all: an igloo is roughly 7x8 blocks with a ~5x6 interior,
 * so it fits well inside a single chunk. Walk in, stand around, walk out and the only
 * chunk-cross samples taken are at the chunk border, which is outside the bounding box
 * — the player would never be credited.
 */
public class StructureListener implements Listener {

    private final OakLog plugin;
    private final ConfigManager configManager;
    private final EntryRegistry registry;
    private final ProgressManager progressManager;

    /**
     * Per-player memo of the structure boxes in the chunk they currently occupy, so the
     * poll doesn't re-query {@link Chunk#getStructures()} every tick-interval. Bounding
     * boxes are worldgen output and never change for a given chunk; the memo is dropped
     * as soon as the player changes chunk (or quits), so a chunk regenerated underneath a
     * standing player — a resource-world reset — is stale only until they move.
     */
    private final Map<UUID, ChunkStructures> chunkCache = new HashMap<>();

    private BukkitTask task;

    public StructureListener(OakLog plugin, ConfigManager configManager, EntryRegistry registry,
                             ProgressManager progressManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.registry = registry;
        this.progressManager = progressManager;
    }

    public void start() {
        int interval = configManager.getStructureCheckInterval();
        if (interval <= 0) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAll, interval, interval);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        chunkCache.clear();
    }

    private void checkAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkStructuresAt(player, player.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        // Compare chunk coords directly rather than via Location#getChunk() — this runs on
        // every move packet, and the chunk objects are only needed on an actual cross.
        if ((from.getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)) {
            return;
        }
        checkStructuresAt(event.getPlayer(), to);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        checkStructuresAt(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        checkStructuresAt(event.getPlayer(), event.getTo());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        checkStructuresAt(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        chunkCache.remove(event.getPlayer().getUniqueId());
    }

    private void checkStructuresAt(Player player, Location location) {
        if (!configManager.isStructureDetectionEnabled()) return;
        if (location == null) return;
        World world = location.getWorld();
        if (world == null) return;

        List<StructureBox> boxes = boxesFor(player, world,
                location.getBlockX() >> 4, location.getBlockZ() >> 4);
        if (boxes.isEmpty()) return;

        Vector playerPos = location.toVector();
        for (StructureBox box : boxes) {
            if (!box.contains(playerPos)) continue;
            NamespacedKey nk = box.key();
            Integer entryId = registry.idFor(nk.getNamespace(), "structure_" + nk.getKey());
            if (entryId == null) continue;
            progressManager.markCollected(player, entryId);
        }
    }

    private List<StructureBox> boxesFor(Player player, World world, int chunkX, int chunkZ) {
        UUID worldId = world.getUID();
        long chunkKey = Chunk.getChunkKey(chunkX, chunkZ);

        ChunkStructures cached = chunkCache.get(player.getUniqueId());
        if (cached != null && cached.chunkKey() == chunkKey && cached.worldId().equals(worldId)) {
            return cached.boxes();
        }

        // PlayerTeleportEvent fires before the player arrives, so getTo() can point at an
        // unloaded chunk — a long-range warp or RTP. Reading it here would force a sync
        // chunk load on the main thread; skip instead and let the poll pick it up once the
        // player is actually there.
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return List.of();
        }

        Collection<GeneratedStructure> structures = world.getChunkAt(chunkX, chunkZ).getStructures();
        List<StructureBox> boxes;
        if (structures.isEmpty()) {
            boxes = List.of();
        } else {
            Registry<Structure> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
            List<StructureBox> collected = new ArrayList<>(structures.size());
            for (GeneratedStructure generated : structures) {
                NamespacedKey nk = reg.getKey(generated.getStructure());
                if (nk == null) continue;
                BoundingBox bb = blockBox(generated);
                collected.add(new StructureBox(nk, bb,
                        isSmallFootprint(bb) || floatsAboveTerrain(world, bb)));
            }
            boxes = List.copyOf(collected);
        }

        chunkCache.put(player.getUniqueId(), new ChunkStructures(worldId, chunkKey, boxes));
        return boxes;
    }

    /**
     * Dumps every input the detection path uses, for {@code /oaklog debug structures}.
     * Deliberately bypasses the cache and re-queries so the output reflects the world,
     * not a memo, and reports the 3x3 chunk neighbourhood so a structure that overlaps
     * the player but is only reported by an adjacent chunk shows up as such.
     */
    public List<String> diagnose(Player player) {
        List<String> out = new ArrayList<>();
        Location loc = player.getLocation();
        World world = loc.getWorld();
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;

        out.add(String.format("pos %.2f %.2f %.2f  world=%s  chunk=%d,%d",
                loc.getX(), loc.getY(), loc.getZ(), world.getName(), cx, cz));
        out.add("poll=" + (task != null ? "RUNNING every " + configManager.getStructureCheckInterval() + "t"
                : "NOT RUNNING") + "  master-switch=" + configManager.isStructureDetectionEnabled());
        boolean gmIgnored = configManager.isGamemodeIgnored(player.getGameMode());
        out.add("gamemode=" + player.getGameMode() + (gmIgnored ? "  <-- IGNORED, collection suppressed" : "  ok"));
        out.add("progress-loaded=" + (progressManager.get(player.getUniqueId()) != null)
                + "  chunk-loaded=" + world.isChunkLoaded(cx, cz)
                + "  surface-y=" + world.getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ()));

        Registry<Structure> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
        Vector pos = loc.toVector();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int qx = cx + dx, qz = cz + dz;
                if (!world.isChunkLoaded(qx, qz)) continue;
                Collection<GeneratedStructure> found = world.getChunkAt(qx, qz).getStructures();
                if (found.isEmpty()) {
                    if (dx == 0 && dz == 0) out.add("chunk " + qx + "," + qz + " (yours): getStructures() -> 0");
                    continue;
                }
                out.add("chunk " + qx + "," + qz + (dx == 0 && dz == 0 ? " (yours)" : "")
                        + ": getStructures() -> " + found.size());
                for (GeneratedStructure gs : found) {
                    NamespacedKey nk = reg.getKey(gs.getStructure());
                    BoundingBox bb = blockBox(gs);
                    Integer id = nk == null ? null
                            : registry.idFor(nk.getNamespace(), "structure_" + nk.getKey());
                    PlayerProgress prog = progressManager.get(player.getUniqueId());
                    boolean small = isSmallFootprint(bb);
                    boolean floating = floatsAboveTerrain(world, bb);
                    boolean xzOnly = small || floating;
                    StructureBox sb = new StructureBox(
                            nk == null ? NamespacedKey.minecraft("unknown") : nk, bb, xzOnly);
                    out.add(String.format("   %s  bb=[%.0f..%.0f, %.0f..%.0f, %.0f..%.0f]  match=%s  contains=%s  entryId=%s  collected=%s",
                            nk == null ? "<unresolved key>" : nk,
                            bb.getMinX(), bb.getMaxX(), bb.getMinY(), bb.getMaxY(), bb.getMinZ(), bb.getMaxZ(),
                            xzOnly ? ("xz-only:" + (small ? "small" : "floating-box")) : "3d",
                            sb.contains(pos), id,
                            (id != null && prog != null) ? prog.isCollected(id) : "?"));
                    if (!xzOnly && bb.contains(pos.getX(), bb.getMinY(), pos.getZ())
                            && !bb.contains(pos)) {
                        out.add("      ^ inside horizontally but not vertically — box may be at the wrong height");
                    }
                }
            }
        }
        return out;
    }

    /**
     * The structure's bounding box, corrected to cover the blocks it names.
     *
     * <p>Vanilla stores structure boxes as <em>inclusive</em> block coordinates, and
     * {@code CraftGeneratedStructure#getBoundingBox} copies {@code minX..maxX} straight
     * into a Bukkit {@link BoundingBox}, whose max is exclusive. Every box therefore
     * stops one block short on +X/+Y/+Z, and a structure that is a single block wide
     * collapses to zero volume: containment then demands the player's coordinates be
     * exactly integral, which standing on a block (x.5, z.5) never is.
     *
     * <p>Buried Treasure is the case that made this visible. Its only piece is a 1x1x1
     * box around the chest, so the entry was uncollectable outright. 45 players held a
     * Heart of the Sea, which drops from nowhere else, and none had the structure.
     */
    private static BoundingBox blockBox(GeneratedStructure generated) {
        BoundingBox raw = generated.getBoundingBox();
        return new BoundingBox(raw.getMinX(), raw.getMinY(), raw.getMinZ(),
                raw.getMaxX() + 1, raw.getMaxY() + 1, raw.getMaxZ() + 1);
    }

    /**
     * A structure start's recorded bounding box can sit at the wrong height. Vanilla's
     * surface template pieces (igloo is the confirmed case) re-derive the terrain height
     * when they place their blocks, move the template down to it, write, then restore the
     * original position — so the saved box keeps the pre-move Y while the building sits
     * somewhere else entirely. Observed live: box y75..94 over an igloo whose floor is at
     * y63, a 27-block error. X and Z are never touched by that move.
     *
     * <p>For a structure with a small horizontal footprint, standing inside its column is
     * already conclusive, so those match on X/Z alone. Anything wider keeps the full 3D
     * test, which is what stops a player walking over a mineshaft from collecting it.
     */
    private boolean isSmallFootprint(BoundingBox box) {
        int limit = configManager.getStructureSmallFootprint();
        if (limit <= 0) return false;
        return box.getWidthX() <= limit && box.getWidthZ() <= limit;
    }

    /**
     * True when the whole box sits above the terrain — nothing generates floating in the
     * sky, so such a box is provably in the wrong place and its height can't be trusted.
     * This catches wide structures the footprint rule skips; a Desert Pyramid measured on
     * the live world had an empty 21x21x15 box with the pyramid itself elsewhere.
     *
     * <p>Only checked against an already-loaded chunk: the box centre can be outside the
     * player's own chunk, and a height lookup would otherwise force a sync chunk load.
     */
    private boolean floatsAboveTerrain(World world, BoundingBox box) {
        int centreX = (int) Math.floor((box.getMinX() + box.getMaxX()) / 2.0);
        int centreZ = (int) Math.floor((box.getMinZ() + box.getMaxZ()) / 2.0);
        if (!world.isChunkLoaded(centreX >> 4, centreZ >> 4)) return false;
        return box.getMinY() > world.getHighestBlockYAt(centreX, centreZ);
    }

    private record StructureBox(NamespacedKey key, BoundingBox box, boolean xzOnly) {
        boolean contains(Vector pos) {
            if (!xzOnly) return box.contains(pos);
            return pos.getX() >= box.getMinX() && pos.getX() <= box.getMaxX()
                    && pos.getZ() >= box.getMinZ() && pos.getZ() <= box.getMaxZ();
        }
    }

    private record ChunkStructures(UUID worldId, long chunkKey, List<StructureBox> boxes) {
    }
}
