package com.bone.engine.extension.studio.infrastructure.persistence.entity;

import com.bone.core.annotation.Id;
import com.bone.core.domain.entity.AbstractEntity;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Metadata SDK 持久化实体：租户目录只读引用（映射共享库 iam_tenant）。
 *
 * <p>仅用于 TenantDirectoryPort 的存在性查询，禁止写入。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Table("iam_tenant")
public class ExtIamTenantDirectory extends AbstractEntity<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  @Column(name = "code")
  private String code;

  @Column(name = "name")
  private String name;
}
