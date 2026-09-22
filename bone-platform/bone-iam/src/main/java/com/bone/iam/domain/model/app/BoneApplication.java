package com.bone.iam.domain.model.app;

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("bone_application")
public class BoneApplication extends TenantAggregateRoot<Long> {
  private String name;
  private String code;
  private String description;
  private String icon;
  private Integer status;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static BoneApplication create(
      String name, String code, String description, String icon, Long tenantId) {
    BoneApplication app = new BoneApplication();
    app.name = name;
    app.code = code;
    app.description = description;
    app.icon = icon;
    app.status = 0;
    app.setTenantId(tenantId);
    app.createdAt = LocalDateTime.now();
    app.updatedAt = LocalDateTime.now();
    return app;
  }

  public void update(String name, String description, String icon, Integer status) {
    if (name != null) this.name = name;
    if (description != null) this.description = description;
    if (icon != null) this.icon = icon;
    if (status != null) this.status = status;
    this.updatedAt = LocalDateTime.now();
  }
}
