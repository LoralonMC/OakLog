package dev.oakheart.oaklog.audit;

import dev.oakheart.oaklog.OakLog;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AuditLog {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final OakLog plugin;
    private final Path folder;

    public AuditLog(OakLog plugin) {
        this.plugin = plugin;
        this.folder = plugin.getDataFolder().toPath();
    }

    public void record(String action, String actor, String detail) {
        String line = String.format("[%s] %-7s actor=%s %s%n",
                LocalDateTime.now().format(TS), action, actor, detail);
        plugin.getLogger().info("[Audit] " + action + " by " + actor + ": " + detail);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> writeLine(line));
    }

    private void writeLine(String line) {
        Path file = folder.resolve("audit-" + LocalDate.now().format(DAY) + ".log");
        try {
            if (!Files.exists(folder)) {
                Files.createDirectories(folder);
            }
            try (OutputStream out = Files.newOutputStream(file,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to write audit log: " + e.getMessage());
        }
    }
}
