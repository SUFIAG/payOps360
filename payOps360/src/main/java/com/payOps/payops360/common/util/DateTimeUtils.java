package com.payops.payops360.common.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for date/time operations.
 */
public final class DateTimeUtils {

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC"));

    private DateTimeUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Get current instant
     */
    public static Instant now() {
        return Instant.now();
    }

    /**
     * Format instant to ISO string
     */
    public static String formatISO(Instant instant) {
        return instant != null ? ISO_FORMATTER.format(instant) : null;
    }

    /**
     * Calculate duration between two instants in seconds
     */
    public static long durationInSeconds(Instant start, Instant end) {
        if (start == null || end == null) {
            return 0;
        }
        return end.getEpochSecond() - start.getEpochSecond();
    }

    /**
     * Calculate duration between two instants in milliseconds
     */
    public static long durationInMillis(Instant start, Instant end) {
        if (start == null || end == null) {
            return 0;
        }
        return end.toEpochMilli() - start.toEpochMilli();
    }

    /**
     * Check if instant is before another
     */
    public static boolean isBefore(Instant instant, Instant other) {
        return instant != null && other != null && instant.isBefore(other);
    }

    /**
     * Check if instant is after another
     */
    public static boolean isAfter(Instant instant, Instant other) {
        return instant != null && other != null && instant.isAfter(other);
    }
}

