package com.bone.engine.extension.studio.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/** 启用内存 Store 实现（见 {@code InMemoryExtensionStore} / {@code InMemoryExtPointStore}）。 */
@Configuration
@ConditionalOnProperty(prefix = "bone.extension.studio.persistence", name = "mode", havingValue = "in-memory", matchIfMissing = true)
@Slf4j
public class StudioInMemoryPersistenceConfiguration {

    public StudioInMemoryPersistenceConfiguration() {
        log.info("Studio persistence mode: in-memory (ExtensionStore / ExtPointStore)");
    }
}
