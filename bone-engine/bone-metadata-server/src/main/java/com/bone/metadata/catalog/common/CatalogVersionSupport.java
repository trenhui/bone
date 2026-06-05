package com.bone.metadata.catalog.common;

import com.bone.metadata.catalog.common.exception.CatalogOptimisticLockException;

/** catalog 乐观锁 version 校验与递增（对齐 AIP-154 / 扩展 StudioVersionSupport）。 */
public final class CatalogVersionSupport {

  private CatalogVersionSupport() {}

  public static void assertExpected(Integer expectedVersion, Integer currentVersion) {
    if (expectedVersion == null) {
      return;
    }
    int current = currentVersion == null ? 0 : currentVersion;
    if (!expectedVersion.equals(current)) {
      throw new CatalogOptimisticLockException(
          "版本冲突：If-Match v" + expectedVersion + "，当前 v" + current);
    }
  }

  public static int nextVersion(Integer currentVersion) {
    return (currentVersion == null ? 0 : currentVersion) + 1;
  }
}
