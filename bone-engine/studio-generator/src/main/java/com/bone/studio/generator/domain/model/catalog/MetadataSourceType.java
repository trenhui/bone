package com.bone.studio.generator.domain.model.catalog;

/** 代码生成元数据来源 */
public enum MetadataSourceType {
  /** JDBC 反向解析物理表（As-Is 默认） */
  PHYSICAL_DB,
  /** 读取 meta_entity / meta_field 已发布快照 */
  CATALOG_SNAPSHOT
}
