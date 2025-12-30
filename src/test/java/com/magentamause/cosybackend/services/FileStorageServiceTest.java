package com.magentamause.cosybackend.services;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class FileStorageServiceTest {

    private final FileStorageService fileStorageService = new FileStorageService();

    @Test
    void resolveAndValidatePath_validPath() {
        Path root = Paths.get("/tmp/root");
        String relative = "subdir/file.txt";

        Path resolved = fileStorageService.resolveAndValidatePath(root, relative);

        assertEquals(root.resolve(relative).normalize(), resolved);
    }

    @Test
    void resolveAndValidatePath_nullRelativePath() {
        Path root = Paths.get("/tmp/root");

        Path resolved = fileStorageService.resolveAndValidatePath(root, null);

        assertEquals(root.normalize(), resolved);
    }

    @Test
    void resolveAndValidatePath_leadingSlash() {
        Path root = Paths.get("/tmp/root");
        String relative = "/subdir/file.txt";

        Path resolved = fileStorageService.resolveAndValidatePath(root, relative);

        assertEquals(root.resolve("subdir/file.txt").normalize(), resolved);
    }

    @Test
    void resolveAndValidatePath_pathTraversal_withinScope() {
        Path root = Paths.get("/tmp/root");
        String relative = "subdir/../file.txt";

        Path resolved = fileStorageService.resolveAndValidatePath(root, relative);

        assertEquals(root.resolve("file.txt").normalize(), resolved);
    }

    @Test
    void resolveAndValidatePath_pathTraversal_outsideScope() {
        Path root = Paths.get("/tmp/root");
        String relative = "../outside.txt";

        assertThrows(
                SecurityException.class,
                () -> {
                    fileStorageService.resolveAndValidatePath(root, relative);
                });
    }

    @Test
    void resolveAndValidatePath_pathTraversal_outsideScope_complex() {
        Path root = Paths.get("/tmp/root");
        String relative = "subdir/../../outside.txt";

        assertThrows(
                SecurityException.class,
                () -> {
                    fileStorageService.resolveAndValidatePath(root, relative);
                });
    }
}
