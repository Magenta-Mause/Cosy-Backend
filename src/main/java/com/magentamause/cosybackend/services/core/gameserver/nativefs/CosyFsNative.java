package com.magentamause.cosybackend.services.core.gameserver.nativefs;

import com.sun.jna.*;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

public interface CosyFsNative extends Library {

    class CosyfsError extends Structure {
        public int code;
        public Pointer message;

        @Override
        protected java.util.List<String> getFieldOrder() {
            return java.util.List.of("code", "message");
        }

        public String messageString() {
            return (message == null) ? null : message.getString(0);
        }
    }

    int cosyfs_read_file(
            String root,
            String rel,
            long maxBytes,
            PointerByReference outPtr,
            LongByReference outLen,
            CosyfsError err);

    int cosyfs_write_file_truncate(
            String root, String rel, Pointer data, long dataLen, int mode, CosyfsError err);

    int cosyfs_rename(String root, String oldRel, String newRel, CosyfsError err);

    int cosyfs_delete_file(String root, String rel, CosyfsError err);

    int cosyfs_set_permissions(
            String root, String rel, int mode, int uid, int gid, CosyfsError err);

    int cosyfs_mkdirs(String root, String rel, int mode, CosyfsError err);

    void cosyfs_free_buf(Pointer p, long len);

    void cosyfs_free_cstring(Pointer s);

    void cosyfs_debug_ping();
}
