package com.magentamause.cosybackend.services.core.gameserver;

import java.nio.file.Path;

record ResolvedBindMount(
        String volumeUuid,
        String containerPathNormalized,
        String innerRelative,
        boolean isRootRequest,
        Path volumeRoot,
        Path requested) {}
