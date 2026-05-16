package dev.oakheart.oaklog.integration;

import dev.oakheart.oaklog.OakLog;
import dev.oakheart.oaklog.model.Category;
import dev.oakheart.oaklog.model.PlayerProgress;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

public class OakLogPlaceholders extends PlaceholderExpansion {

    private final OakLog plugin;

    public OakLogPlaceholders(OakLog plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "oaklog";
    }

    @Override
    public String getAuthor() {
        return "Loralon";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";
        String key = params.toLowerCase(Locale.ROOT);

        switch (key) {
            case "total_collected": {
                PlayerProgress progress = plugin.getProgressManager().get(player.getUniqueId());
                int n = progress == null
                        ? plugin.getDataStore().collectedCountOverall(player.getUniqueId())
                        : progress.collectedCount();
                return String.valueOf(n);
            }
            case "total_entries":
                return String.valueOf(plugin.getEntryRegistry().liveCount());
            case "total_percent":
                return percent(getCollectedTotal(player), plugin.getEntryRegistry().liveCount());
            case "toast_state": {
                PlayerProgress progress = plugin.getProgressManager().get(player.getUniqueId());
                if (progress != null) return progress.toastState().id();
                PlayerProgress.ToastState fallback =
                        plugin.getDataStore().loadToastState(player.getUniqueId());
                return (fallback != null ? fallback.id() : plugin.getConfigManager().getToastDefaultState());
            }
            case "rank":
                return String.valueOf(plugin.getLeaderboardManager().rankOverall(player.getUniqueId()));
            default:
                break;
        }

        if (key.startsWith("progress_")) {
            Category category = Category.fromId(key.substring("progress_".length()));
            if (category == null) return "";
            int collected = plugin.getProgressManager().collectedCount(player.getUniqueId(), category);
            int total = plugin.getEntryRegistry().liveCountFor(category);
            return collected + "/" + total;
        }
        if (key.startsWith("percent_")) {
            Category category = Category.fromId(key.substring("percent_".length()));
            if (category == null) return "";
            int collected = plugin.getProgressManager().collectedCount(player.getUniqueId(), category);
            int total = plugin.getEntryRegistry().liveCountFor(category);
            return percent(collected, total);
        }

        return null;
    }

    private int getCollectedTotal(OfflinePlayer player) {
        PlayerProgress progress = plugin.getProgressManager().get(player.getUniqueId());
        if (progress != null) return progress.collectedCount();
        return plugin.getDataStore().collectedCountOverall(player.getUniqueId());
    }

    private static String percent(int collected, int total) {
        if (total <= 0) return "0.0";
        return String.format(Locale.ROOT, "%.1f", 100.0 * collected / total);
    }
}
