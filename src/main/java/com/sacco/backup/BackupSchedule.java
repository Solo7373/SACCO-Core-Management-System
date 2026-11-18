package com.sacco.backup;

import java.time.Duration;
import java.util.Locale;

/**
 * Automatic backup cadence selected during first-run setup. This mirrors the
 * WhatsApp backup model: the database is copied automatically in the
 * background, without staff having to press a button.
 */
public enum BackupSchedule {

    HOURLY(Duration.ofHours(1)),
    DAILY(Duration.ofDays(1)),
    WEEKLY(Duration.ofDays(7));

    private final Duration interval;

    BackupSchedule(Duration interval) {
        this.interval = interval;
    }

    public Duration interval() {
        return interval;
    }

    /** Unknown or blank values fall back to hourly, the setup default. */
    public static BackupSchedule from(String value) {
        if (value == null || value.isBlank()) {
            return HOURLY;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return HOURLY;
        }
    }
}
