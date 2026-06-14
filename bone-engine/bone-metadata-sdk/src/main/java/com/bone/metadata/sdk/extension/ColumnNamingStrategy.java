package com.bone.metadata.sdk.extension;

import com.bone.metadata.sdk.domain.enums.DataType;

/** 列命名策略。根据字段数据类型和索引生成物理列名。 */
public interface ColumnNamingStrategy {
  /**
   * 生成物理列名，例如 "ext_string_01"。
   *
   * @param type 数据类型
   * @param index 分配的序号（从 1 开始）
   * @return 生成的列名
   */
  String generate(DataType type, int index);
}
