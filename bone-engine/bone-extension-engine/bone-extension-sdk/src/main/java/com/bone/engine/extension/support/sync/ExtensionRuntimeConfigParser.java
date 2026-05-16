package com.bone.engine.extension.support.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/** 解析 Studio 扩展配置 JSON。 */
@Slf4j
public final class ExtensionRuntimeConfigParser {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ExtensionRuntimeConfigParser() {}

  @NonNull
  public static ExtensionRuntimeConfig parse(@Nullable String configJson) {
    if (!StringUtils.hasText(configJson)) {
      return new ExtensionRuntimeConfig();
    }
    try {
      return MAPPER.readValue(configJson.trim(), ExtensionRuntimeConfig.class);
    } catch (Exception e) {
      log.warn("Failed to parse extension runtime config JSON, using defaults: {}", e.getMessage());
      return new ExtensionRuntimeConfig();
    }
  }
}
