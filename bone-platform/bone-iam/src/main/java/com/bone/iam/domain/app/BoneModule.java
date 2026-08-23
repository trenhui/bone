package com.bone.iam.domain.app;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("bone_module")
public class BoneModule extends TenantAggregateRoot<Long> {
  private Long appId;
  private String name;
  private String code;
  private String description;
  private Integer status;
  private Integer sortOrder;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static BoneModule create(
      Long appId, String name, String code, String description, Long tenantId) {
    BoneModule mod = new BoneModule();
    mod.appId = appId;
    mod.name = name;
    mod.code = code;
    mod.description = description;
    mod.status = 0;
    mod.sortOrder = 0;
    mod.setTenantId(tenantId);
    mod.createdAt = LocalDateTime.now();
    mod.updatedAt = LocalDateTime.now();
    return mod;
  }

  public void update(String name, String description, Integer status) {
    if (name != null) this.name = name;
    if (description != null) this.description = description;
    if (status != null) this.status = status;
    this.updatedAt = LocalDateTime.now();
  }
}
