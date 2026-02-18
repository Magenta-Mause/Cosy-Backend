package com.magentamause.cosybackend.services.core.gameserver.nativefs;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Locale;

public final class NativeLibraryLoader {

    private NativeLibraryLoader() {}

    public static <T extends Library> T loadFromResources(String baseName, Class<T> iface) {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

        if (!os.contains("linux")) {
            throw new IllegalStateException(
                    "cosyfs native library only implemented for Linux. Detected: " + os);
        }

        String platformDir = "linux-" + mapArch(arch);
        String fileName = "lib" + baseName + ".so";
        String resourcePath = "/native/" + platformDir + "/" + fileName;

        Path extractedLib = extractResource(resourcePath, fileName);
        Path extractedDir = extractedLib.getParent();

        NativeLibrary.addSearchPath(baseName, extractedDir.toAbsolutePath().toString());
        return Native.load(baseName, iface);
    }

    private static String mapArch(String arch) {
        if (arch.contains("amd64") || arch.contains("x86_64")) return "x86_64";
        if (arch.contains("aarch64") || arch.contains("arm64")) return "aarch64";
        throw new IllegalStateException("Unsupported arch for cosyfs native library: " + arch);
    }

    private static Path extractResource(String resourcePath, String fileName) {
        try (InputStream in = NativeLibraryLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Native library resource not found: " + resourcePath);
            }

            Path dir = Files.createTempDirectory("cosyfs-native-");
            dir.toFile().deleteOnExit();

            Path out = dir.resolve(fileName);
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);

            out.toFile().setReadable(true, true);
            out.toFile().setExecutable(true, true);
            out.toFile().deleteOnExit();

            return out;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to extract native library from " + resourcePath, e);
        }
    }
}
