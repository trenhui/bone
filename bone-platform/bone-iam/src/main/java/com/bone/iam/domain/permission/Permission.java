package com.bone.iam.domain.permission;

import com.bone.core.domain.AggregateRoot;
import com.bone.iam.domain.permission.event.PermissionCreatedEvent;
import com.bone.iam.domain.permission.vo.PermissionType;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("iam_permission")
public class Permission extends AggregateRoot<Long> {
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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static Permission create(String code, String name, String description,
                                     String resourceType, String resourcePath, String action,
                                     Long parentId, PermissionType type, int sortOrder) {
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
        permission.createTime = LocalDateTime.now();
        permission.updateTime = LocalDateTime.now();
        permission.addDomainEvent(new PermissionCreatedEvent(permission));
        return permission;
    }

    public void update(String name, String description, String resourceType, String resourcePath,
                        String action, Long parentId, PermissionType type, int sortOrder) {
        this.name = name;
        this.description = description;
        this.resourceType = resourceType;
        this.resourcePath = resourcePath;
        this.action = action;
        this.parentId = parentId;
        this.type = type;
        this.sortOrder = sortOrder;
        this.updateTime = LocalDateTime.now();
    }
}
