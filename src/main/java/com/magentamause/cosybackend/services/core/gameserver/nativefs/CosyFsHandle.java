package com.magentamause.cosybackend.services.core.gameserver.nativefs;

import java.util.Optional;

public record CosyFsHandle(Optional<CosyFsNative> lib) {
  public boolean available() {
    return lib != null && lib.isPresent();
  }

  public CosyFsNative require() {
    return lib.orElseThrow();
  }
}
