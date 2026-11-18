package com.sacco.backup;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class BackupScheduleTest {

    @Test
    void mapsTheSchedulesStoredDuringSetup() {
        assertThat(BackupSchedule.from("HOURLY").interval()).isEqualTo(Duration.ofHours(1));
        assertThat(BackupSchedule.from("daily").interval()).isEqualTo(Duration.ofDays(1));
        assertThat(BackupSchedule.from(" WEEKLY ").interval()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void blankOrUnknownValuesFallBackToHourly() {
        assertThat(BackupSchedule.from(null)).isEqualTo(BackupSchedule.HOURLY);
        assertThat(BackupSchedule.from("")).isEqualTo(BackupSchedule.HOURLY);
        assertThat(BackupSchedule.from("EVERY_FIFTEEN_MINUTES")).isEqualTo(BackupSchedule.HOURLY);
    }
}
