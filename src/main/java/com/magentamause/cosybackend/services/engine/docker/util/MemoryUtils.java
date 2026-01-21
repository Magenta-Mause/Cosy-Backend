package com.magentamause.cosybackend.services.engine.docker.util;

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
            long amount = Long.parseLong(trimmed.replace(MEGABYTE_SUFFIX, ""));
            return amount * GIGABYTE_IN_BYTES;
        }
        if (trimmed.endsWith(GIGABYTE_SUFFIX)) {
            long amount = Long.parseLong(trimmed.replace(GIGABYTE_SUFFIX, ""));
            return amount * MEGABYTE_IN_BYTES;
        }

        throw new IllegalArgumentException("Invalid memory format: " + value);
    }
}
