package com.bone.engine.extension.support.sync;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/** 扩展 code 解析（与 {@link com.bone.engine.extension.core.register.ExtensionRegister} 规则对齐）。 */
public final class ExtensionCodeResolver {

  public static final String DEFAULT_EXTENSION_CODE_PREFIX = "EXT_";

  private ExtensionCodeResolver() {}

  @NonNull
  public static String resolve(
      @Nullable String customName,
      @Nullable String implementationClass,
      @Nullable Long fallbackId) {
    if (StringUtils.hasText(customName)) {
      return customName.trim();
    }
    String fromClass = fromImplementationClass(implementationClass);
    if (fromClass != null) {
      return fromClass;
    }
    return DEFAULT_EXTENSION_CODE_PREFIX + (fallbackId != null ? fallbackId : "unknown");
  }

  @Nullable
  public static String fromImplementationClass(@Nullable String implementationClass) {
    if (!StringUtils.hasText(implementationClass)) {
      return null;
    }
    String className = implementationClass.trim();
    int dot = className.lastIndexOf('.');
    return DEFAULT_EXTENSION_CODE_PREFIX + (dot >= 0 ? className.substring(dot + 1) : className);
  }

  /** Studio 配置的 code 是否与运行时注册 code 一致（name 或类名推导）。 */
  public static boolean alignsWithRuntime(
      @Nullable String studioName,
      @Nullable String implementationClass,
      @NonNull String runtimeCode) {
    if (!StringUtils.hasText(runtimeCode)) {
      return false;
    }
    if (StringUtils.hasText(studioName) && runtimeCode.equals(studioName.trim())) {
      return true;
    }
    String fromClass = fromImplementationClass(implementationClass);
    return fromClass != null && runtimeCode.equals(fromClass);
  }
}
