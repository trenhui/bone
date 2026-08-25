package com.bone.metadata.catalog.domain.model;

import com.bone.core.domain.entity.AbstractEntity;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 只读引用实体：指向 IAM 模块上下文独占的 {@code bone_module} 表。
 *
 * <p>领域边界：模块(Module)聚合根归属 IAM 上下文（bone-iam），metadata 上下文不再拥有 {@code BoneModule}。本类仅为 metadata
 * 侧在写入实体(Entity)/字段(Field)时提供"模块存在性" 校验所需的只读视图，不含任何写操作，避免 metadata 重复定义模块建模。
 */
@Getter
@NoArgsConstructor
@Table("bone_module")
public class IamModuleRef extends AbstractEntity<Long> {

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "app_id", nullable = false)
  private Long appId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "code", nullable = false)
  private String code;
}
