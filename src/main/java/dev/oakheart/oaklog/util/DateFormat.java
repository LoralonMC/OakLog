package dev.oakheart.oaklog.util;

import dev.oakheart.oaklog.config.ConfigManager;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DateFormat {

    private final ConfigManager configManager;

    public DateFormat(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public String format(long epochMillis) {
        ZoneId zone = resolveZone();
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(configManager.getDateFormat(), Locale.ROOT).withZone(zone);
        } catch (IllegalArgumentException e) {
            formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(zone);
        }
        return formatter.format(Instant.ofEpochMilli(epochMillis));
    }

    private ZoneId resolveZone() {
        String tz = configManager.getDateTimezone();
        if (tz == null || tz.isBlank() || tz.equalsIgnoreCase("system")) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }
}
