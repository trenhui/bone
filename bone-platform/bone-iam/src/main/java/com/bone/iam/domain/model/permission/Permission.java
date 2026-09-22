package com.bone.iam.domain.model.permission;

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.iam.domain.model.permission.event.PermissionCreatedEvent;
import com.bone.iam.domain.model.permission.valueobject.PermissionType;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("iam_permission")
public class Permission extends AggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;

  private String code;
  private String name;
  private String description;
  private String resourceType;
  private String resourcePath;
  private String action;
  private Long parentId;
  private PermissionType type;
  private int sortOrder;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static Permission create(
      String code,
      String name,
      String description,
      String resourceType,
      String resourcePath,
      String action,
      Long parentId,
      PermissionType type,
      int sortOrder) {
    Permission permission = new Permission();
    permission.code = code;
    permission.name = name;
    permission.description = description;
    permission.resourceType = resourceType;
    permission.resourcePath = resourcePath;
    permission.action = action;
    permission.parentId = parentId;
    permission.type = type;
    permission.sortOrder = sortOrder;
    permission.createdAt = LocalDateTime.now();
    permission.updatedAt = LocalDateTime.now();
    permission.addDomainEvent(new PermissionCreatedEvent(permission));
    return permission;
  }

  public void update(
      String name,
      String description,
      String resourceType,
      String resourcePath,
      String action,
      Long parentId,
      PermissionType type,
      int sortOrder) {
    if (name != null) {
      this.name = name;
    }
    this.description = description;
    if (resourceType != null) {
      this.resourceType = resourceType;
    }
    if (resourcePath != null) {
      this.resourcePath = resourcePath;
    }
    if (action != null) {
      this.action = action;
    }
    this.parentId = parentId;
    if (type != null) {
      this.type = type;
    }
    this.sortOrder = sortOrder;
    this.updatedAt = LocalDateTime.now();
  }
}
