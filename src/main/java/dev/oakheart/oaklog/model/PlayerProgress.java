package dev.oakheart.oaklog.model;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PlayerProgress {

    public static final int RECENT_BUFFER_SIZE = 64;

    private final UUID playerUuid;
    private final Set<Integer> collected;
    private final Deque<RecentEntry> recent;
    private ToastState toastState;

    public PlayerProgress(UUID playerUuid, Set<Integer> collected, List<RecentEntry> recent, ToastState toastState) {
        this.playerUuid = playerUuid;
        this.collected = new HashSet<>(collected);
        this.recent = new ArrayDeque<>(recent);
        this.toastState = toastState == null ? ToastState.OFF : toastState;
    }

    public UUID playerUuid() {
        return playerUuid;
    }

    public synchronized boolean isCollected(int entryId) {
        return collected.contains(entryId);
    }

    public synchronized int collectedCount() {
        return collected.size();
    }

    public synchronized Set<Integer> snapshotCollected() {
        return Set.copyOf(collected);
    }

    public synchronized List<RecentEntry> snapshotRecent() {
        return List.copyOf(recent);
    }

    public synchronized boolean markCollected(int entryId, long collectedAt) {
        if (!collected.add(entryId)) {
            return false;
        }
        recent.addFirst(new RecentEntry(entryId, collectedAt));
        while (recent.size() > RECENT_BUFFER_SIZE) {
            recent.removeLast();
        }
        return true;
    }

    public synchronized boolean removeCollected(int entryId) {
        if (!collected.remove(entryId)) {
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
