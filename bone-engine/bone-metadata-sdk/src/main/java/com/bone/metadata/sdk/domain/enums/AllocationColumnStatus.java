package com.bone.metadata.sdk.domain.enums;

/** 列分配的状态枚举 */
public enum AllocationColumnStatus {
  /** 刚创建或回收后可用于分配 */
  AVAILABLE,
  /** 已被上层业务分配并正在使用 */
  IN_USE,
  /** 已被释放，可重新分配 */
  RECYCLED
}
