package com.magentamause.cosybackend.services.core.gameserver;

import com.magentamause.cosybackend.services.core.gameserver.nativefs.CosyFsHandle;
import com.magentamause.cosybackend.services.core.gameserver.nativefs.CosyFsNative;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import java.nio.file.Path;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Component
@RequiredArgsConstructor
class GameServerNativeOps {

    private final CosyFsHandle cosyFsHandle;

    boolean nativeAvailable() {
        return cosyFsHandle.available();
    }

    private CosyFsNative nativeLib() {
        return cosyFsHandle.require();
    }

    RuntimeException mapNativeErr(CosyFsNative.CosyfsError err, String fallbackMsg) {
        String msg = fallbackMsg;
        int code = 0;

        try {
            if (err != null) {
                try {
                    err.read();
                } catch (Throwable ignored) {
                }

                code = err.code;
                String m = err.messageString();
                if (m != null && !m.isBlank()) {
                    msg = m;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            try {
                if (err != null) {
                    try {
                        err.read();
                    } catch (Throwable ignored) {
                    }
                    if (err.message != null) {
                        nativeLib().cosyfs_free_cstring(err.message);
                        err.message = null;
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        return switch (code) {
            case 2 -> new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
            case 13 -> new ResponseStatusException(HttpStatus.FORBIDDEN, msg);
            case 17 -> new ResponseStatusException(HttpStatus.CONFLICT, msg);
            case 21 -> new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
            case 22 -> new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
            case 27 -> new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, msg);
            default -> new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, msg + " (errno=" + code + ")");
        };
    }

    Optional<byte[]> tryReadFileNative(Path volumeRoot, Path rel, long maxBytes) {
        if (!nativeAvailable()) return Optional.empty();

        String relStr = rel.toString().replace("\\", "/");

        CosyFsNative.CosyfsError err = new CosyFsNative.CosyfsError();
        PointerByReference outPtr = new PointerByReference();
        LongByReference outLen = new LongByReference();

        Pointer p = null;
        long len = 0;

        try {
            err.write();

            int rc =
                    nativeLib()
                            .cosyfs_read_file(
                                    volumeRoot.toString(), relStr, maxBytes, outPtr, outLen, err);

            err.read();

            len = outLen.getValue();
            p = outPtr.getValue();

            if (rc != 0) {
                throw mapNativeErr(err, "Native read failed: " + relStr);
            }

            if (len < 0 || len > Integer.MAX_VALUE) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE, "File too large to read into memory");
            }

            return Optional.of(p == null ? new byte[0] : p.getByteArray(0, (int) len));

        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            log.warn("Native cosyfs not available for read (falling back): {}", e.toString());
            return Optional.empty();

        } finally {
            if (p != null) {
                try {
                    long freeLen = Math.max(0, len);
                    nativeLib().cosyfs_free_buf(p, freeLen);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    boolean tryWriteFileNative(Path volumeRoot, Path rel, byte[] content, int mode) {
        if (!nativeAvailable()) return false;

        String relStr = rel.toString().replace("\\", "/");
        CosyFsNative.CosyfsError err = new CosyFsNative.CosyfsError();

        try {
            Memory mem = new Memory(Math.max(content.length, 1));
            if (content.length > 0) {
                mem.write(0, content, 0, content.length);
            }

            err.write();

            int rc =
                    nativeLib()
                            .cosyfs_write_file_truncate(
                                    volumeRoot.toString(), relStr, mem, content.length, mode, err);

            err.read();

            if (rc != 0) {
                throw mapNativeErr(err, "Native write failed: " + relStr);
            }
            return true;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            log.warn("Native cosyfs not available for write (falling back): {}", e.toString());
            return false;
        }
    }

    boolean tryRenameNative(Path volumeRoot, Path oldRel, Path newRel) {
        if (!nativeAvailable()) return false;

        String oldStr = oldRel.toString().replace("\\", "/");
        String newStr = newRel.toString().replace("\\", "/");
        CosyFsNative.CosyfsError err = new CosyFsNative.CosyfsError();

        try {
            err.write();

            int rc = nativeLib().cosyfs_rename(volumeRoot.toString(), oldStr, newStr, err);

            err.read();

            if (rc != 0) {
                throw mapNativeErr(err, "Native rename failed");
            }
            return true;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            log.warn("Native cosyfs not available for rename (falling back): {}", e.toString());
            return false;
        }
    }

    boolean tryDeleteFileNative(Path volumeRoot, Path rel) {
        if (!nativeAvailable()) return false;

        String relStr = rel.toString().replace("\\", "/");
        CosyFsNative.CosyfsError err = new CosyFsNative.CosyfsError();

        try {
            err.write();

            int rc = nativeLib().cosyfs_delete_file(volumeRoot.toString(), relStr, err);

            err.read();

            if (rc != 0) {
                throw mapNativeErr(err, "Native delete failed: " + relStr);
            }
            return true;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            log.warn("Native cosyfs not available for delete (falling back): {}", e.toString());
            return false;
        }
    }

    /**
     * Sets the mode and/or owner of {@code rel} via the native library, which resolves the target
     * with openat2 (symlink-free, beneath root) and applies the change to the resulting fd, so
     * there is no path re-walk or TOCTOU. A {@code null} uid skips the chown. Returns {@code false}
     * only when the native library is unavailable; a genuine native error (e.g. a rejected symlink)
     * throws rather than falling back.
     */
    boolean trySetPermissionsNative(Path volumeRoot, Path rel, int mode, Integer uid) {
        if (!nativeAvailable()) return false;

        String relStr = rel.toString().replace("\\", "/");
        CosyFsNative.CosyfsError err = new CosyFsNative.CosyfsError();

        int uidArg = (uid == null) ? -1 : uid;
        int gidArg = (uid == null) ? -1 : uid;

        try {
            err.write();

            int rc =
                    nativeLib()
                            .cosyfs_set_permissions(
                                    volumeRoot.toString(), relStr, mode, uidArg, gidArg, err);

            err.read();

            if (rc != 0) {
                throw mapNativeErr(err, "Native set-permissions failed: " + relStr);
            }
            return true;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            log.warn(
                    "Native cosyfs not available for set-permissions (falling back): {}",
                    e.toString());
            return false;
        }
    }

    /**
     * Creates {@code rel} and any missing parents via the native library, without following or
     * creating through symlinks. Returns {@code false} only when the native library is unavailable;
     * a genuine native error (e.g. a symlinked path component) throws rather than falling back.
     */
    boolean tryMkdirsNative(Path volumeRoot, Path rel, int mode) {
        if (!nativeAvailable()) return false;

        String relStr = rel.toString().replace("\\", "/");
        CosyFsNative.CosyfsError err = new CosyFsNative.CosyfsError();

        try {
            err.write();

            int rc = nativeLib().cosyfs_mkdirs(volumeRoot.toString(), relStr, mode, err);

            err.read();

            if (rc != 0) {
                throw mapNativeErr(err, "Native mkdirs failed: " + relStr);
            }
            return true;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            log.warn("Native cosyfs not available for mkdirs (falling back): {}", e.toString());
            return false;
        }
    }
}
