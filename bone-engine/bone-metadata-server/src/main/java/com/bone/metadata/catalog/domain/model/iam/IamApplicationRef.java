package com.bone.metadata.catalog.domain.model.iam;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 只读引用实体：指向 IAM 模块独占的 {@code bone_application} 表。
 *
 * <p>领域边界：应用(App)聚合根归属 IAM 上下文（bone-iam），metadata 上下文不再拥有 {@code BoneApplication}。本类仅为 metadata
 * 侧在写入模块(Module)时提供"应用存在性" 校验所需的只读视图，不含任何写操作，避免 metadata 重复定义应用建模。
 */
@Getter
@NoArgsConstructor
@Table("bone_application")
public class IamApplicationRef extends AbstractEntity<Long> {

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "name", nullable = false)
  private String name;
}
