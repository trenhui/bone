package com.bone.engine.extension.studio.application.service;

import com.bone.engine.extension.studio.common.exception.OptimisticLockException;

/** 乐观锁 version 校验与递增。 */
public final class StudioVersionSupport {

  private StudioVersionSupport() {}

  public static void assertExpected(Integer expectedVersion, Integer currentVersion) {
    if (expectedVersion == null) {
      return;
    }
    int current = currentVersion == null ? 1 : currentVersion;
    if (!expectedVersion.equals(currentVersion)) {
      throw new OptimisticLockException("版本冲突：If-Match v" + expectedVersion + "，当前 v" + current);
    }
  }

  public static Integer nextVersion(Integer currentVersion) {
    return currentVersion == null ? 2 : currentVersion + 1;
  }
}
