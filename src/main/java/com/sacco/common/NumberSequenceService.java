package com.sacco.common;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Year;

@Service
@RequiredArgsConstructor
public class NumberSequenceService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Atomically increments and returns the next number for a named sequence and year.
     * The upsert works safely even when two requests generate a number at the same time.
     */
    public int next(String sequenceName, int year) {
        Integer next = jdbcTemplate.queryForObject("""
                INSERT INTO number_sequences (sequence_name, year, last_number)
                VALUES (?, ?, 1)
                ON CONFLICT (sequence_name, year)
                DO UPDATE SET last_number = number_sequences.last_number + 1
                RETURNING last_number
                """, Integer.class, sequenceName, year);
        if (next == null) {
            throw new IllegalStateException("Could not generate the next number for " + sequenceName);
        }
        return next;
    }

    public String formatted(String sequenceName, String prefix) {
        int year = Year.now().getValue();
        return String.format("%s-%d-%04d", prefix, year, next(sequenceName, year));
    }
}
