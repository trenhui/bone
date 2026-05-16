package com.bone.engine.extension.support.sync;

/** Studio 与 SDK 运行时共享的 Redis Key 约定。 */
public final class ExtensionMetadataKeys {

  public static final String META_PREFIX = "bone:ext:meta:";
  public static final String META_INDEX_PREFIX = "bone:ext:meta:index:";
  public static final String REFRESH_CHANNEL = "bone:ext:metadata:refresh";

  private ExtensionMetadataKeys() {}

  public static String metadataKey(String extensionPoint, String code) {
    return META_PREFIX + extensionPoint + ":" + code;
  }

  public static String indexKey(String extensionPoint) {
    return META_INDEX_PREFIX + extensionPoint;
  }
}
