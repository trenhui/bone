package com.bone.metadata.engine;

/** 元数据变更类型枚举 定义元数据可能发生的变更类型 */
public enum MetadataChangeType {

  /** 创建新元数据 */
  CREATE,

  /** 更新现有元数据 */
  UPDATE,

  /** 删除元数据 */
  DELETE,

  /** 启用元数据 */
  ENABLE,

  /** 禁用元数据 */
  DISABLE
}
