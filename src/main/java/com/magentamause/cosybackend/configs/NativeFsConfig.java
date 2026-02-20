package com.magentamause.cosybackend.configs;

import com.magentamause.cosybackend.services.core.gameserver.nativefs.CosyFsHandle;
import com.magentamause.cosybackend.services.core.gameserver.nativefs.CosyFsNative;
import com.magentamause.cosybackend.services.core.gameserver.nativefs.NativeLibraryLoader;
import java.util.Optional;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NativeFsConfig {

    @Bean
    public CosyFsHandle cosyFsHandle() {
        try {
            CosyFsNative lib = NativeLibraryLoader.loadFromResources("cosyfs", CosyFsNative.class);
            return new CosyFsHandle(Optional.of(lib));
        } catch (Throwable t) {
            LoggerFactory.getLogger(NativeFsConfig.class)
                    .warn("cosyfs native library not available; falling back to Java NIO", t);
            return new CosyFsHandle(Optional.empty());
        }
    }
}
