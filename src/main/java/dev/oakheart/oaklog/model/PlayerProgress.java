package dev.oakheart.oaklog.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerProgress {

    public static final int RECENT_BUFFER_SIZE = 64;

    private final UUID playerUuid;
    // entryId -> collected-at timestamp (epoch millis) for the FULL collected set.
    // The `recent` buffer only retains the newest RECENT_BUFFER_SIZE entries, so it
    // cannot be relied on to look up the obtained-date of an arbitrary entry.
    private final Map<Integer, Long> collected;
    private final Deque<RecentEntry> recent;
    private ToastState toastState;

    public PlayerProgress(UUID playerUuid, Map<Integer, Long> collected, List<RecentEntry> recent, ToastState toastState) {
        this.playerUuid = playerUuid;
        this.collected = new HashMap<>(collected);
        this.recent = new ArrayDeque<>(recent);
        this.toastState = toastState == null ? ToastState.OFF : toastState;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public synchronized boolean isCollected(int entryId) {
        return collected.containsKey(entryId);
    }

    /**
     * The epoch-millis timestamp at which the given entry was collected, or
     * {@code null} if it has not been collected. Backed by the full collected
     * set, so it is correct for every entry — not just the recent buffer.
     */
    public synchronized Long collectedAt(int entryId) {
        return collected.get(entryId);
    }

    public synchronized int collectedCount() {
        return collected.size();
    }

    public synchronized Set<Integer> snapshotCollected() {
        return Set.copyOf(collected.keySet());
    }

    public synchronized List<RecentEntry> snapshotRecent() {
        return List.copyOf(recent);
    }

    public synchronized boolean markCollected(int entryId, long collectedAt) {
        if (collected.putIfAbsent(entryId, collectedAt) != null) {
            return false;
        }
        recent.addFirst(new RecentEntry(entryId, collectedAt));
        while (recent.size() > RECENT_BUFFER_SIZE) {
            recent.removeLast();
        }
        return true;
    }

    public synchronized boolean removeCollected(int entryId) {
        if (collected.remove(entryId) == null) {
            return false;
        }
        recent.removeIf(r -> r.entryId() == entryId);
        return true;
    }

    public synchronized ToastState toastState() {
        return toastState;
    }

    public synchronized void setToastState(ToastState state) {
        this.toastState = state == null ? ToastState.OFF : state;
    }

    public enum ToastState {
        ON,
        OFF,
        MILESTONES;

        public static ToastState fromString(String s) {
            if (s == null) return OFF;
            return switch (s.toLowerCase()) {
                case "on" -> ON;
                case "milestones" -> MILESTONES;
                default -> OFF;
            };
        }

        public String id() {
            return switch (this) {
                case ON -> "on";
                case OFF -> "off";
                case MILESTONES -> "milestones";
            };
        }
    }
}
