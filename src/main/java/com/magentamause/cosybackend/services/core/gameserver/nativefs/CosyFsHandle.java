package com.magentamause.cosybackend.services.core.gameserver.nativefs;

import java.util.Objects;
import java.util.Optional;

public record CosyFsHandle(Optional<CosyFsNative> lib) {
    public CosyFsHandle {
        lib = Objects.requireNonNull(lib, "lib cannot be null");
    }

    public boolean available() {
        return lib.isPresent();
    }

    public CosyFsNative require() {
        return lib.orElseThrow();
    }
}
