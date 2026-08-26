package com.bone.metadata.engine.runtime.platform;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 默认空实现：宿主未注入 SDK 桥接时使用。 */
public class NoopMetadataPlatformBridge implements MetadataPlatformBridge {

  private static final Logger log = LoggerFactory.getLogger(NoopMetadataPlatformBridge.class);

  @Override
  public Optional<String> loadPublishedEntityJson(Long tenantId, String entityCode) {
    log.debug("MetadataPlatformBridge noop: tenantId={}, entityCode={}", tenantId, entityCode);
    return Optional.empty();
  }
}
