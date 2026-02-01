package com.magentamause.cosybackend.services.engine.docker.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MemoryUtils {
    private static final String GIGABYTE_SUFFIX = "GiB";
    private static final String MEGABYTE_SUFFIX = "MiB";
    private static final long GIGABYTE_IN_BYTES = 1024L * 1024L * 1024L;
    private static final long MEGABYTE_IN_BYTES = 1024L * 1024L;

    /** Convert a memory string like "512MiB" or "2GiB" to bytes. */
    public static long parseMemoryStringToBytes(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Memory string cannot be null");
        }

        String trimmed = value.trim();
        if (trimmed.endsWith(MEGABYTE_SUFFIX)) {
            double amount =
                    Double.parseDouble(
                            trimmed.substring(0, trimmed.length() - MEGABYTE_SUFFIX.length()));
            return (long) amount * MEGABYTE_IN_BYTES;
        } else if (trimmed.endsWith(GIGABYTE_SUFFIX)) {
            double amount =
                    Double.parseDouble(
                            trimmed.substring(0, trimmed.length() - GIGABYTE_SUFFIX.length()));
            return (long) amount * GIGABYTE_IN_BYTES;
        }

        throw new IllegalArgumentException("Invalid memory format: " + value);
    }

    public static String formatBytesToReadableString(long bytes) {
        if (bytes >= GIGABYTE_IN_BYTES) {
            double gib = (double) bytes / GIGABYTE_IN_BYTES;
            // if it's a whole number, print as integer
            if (gib % 1 == 0) {
                return String.format("%.0f%s", gib, GIGABYTE_SUFFIX);
            }
            return String.format("%.2f%s", gib, GIGABYTE_SUFFIX);
        }
        return (bytes / MEGABYTE_IN_BYTES) + MEGABYTE_SUFFIX;
    }
}
