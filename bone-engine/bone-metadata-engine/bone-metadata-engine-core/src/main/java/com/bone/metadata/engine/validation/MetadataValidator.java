package com.bone.metadata.engine.validation;

import com.bone.metadata.engine.domain.metadata.EntityMetadata;

/** 元数据验证器接口 用于验证元数据的合法性 */
public interface MetadataValidator {

  /**
   * 验证元数据
   *
   * @param metadata 元数据对象
   * @return 是否验证通过
   */
  boolean validate(EntityMetadata metadata);
}
