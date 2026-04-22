package com.bone.iam.domain.model.permission;

import com.bone.core.domain.AggregateRoot;
import com.bone.iam.domain.model.permission.event.PermissionCreatedEvent;
import com.bone.iam.domain.model.permission.vo.*;
import com.bone.core.util.DistributedIdGenerator;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Permission extends AggregateRoot<PermissionId> {
    private PermissionId id;
    private Long dbId;
    private PermissionCode code;
    private String name;
    private String description;
    private String parentId;
    private PermissionType type;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static Permission create(PermissionCode code, String name, String description, String parentId, PermissionType type) {
        Permission permission = new Permission();
        permission.id = PermissionId.of(DistributedIdGenerator.generateUuid());
        permission.code = code;
        permission.name = name;
        permission.description = description;
        permission.parentId = parentId;
        permission.type = type;
        permission.createTime = LocalDateTime.now();
        permission.updateTime = LocalDateTime.now();
        permission.addDomainEvent(new PermissionCreatedEvent(permission));
        return permission;
    }

    public void update(String name, String description, String parentId, PermissionType type) {
        this.name = name;
        this.description = description;
        this.parentId = parentId;
        this.type = type;
        this.updateTime = LocalDateTime.now();
    }

    void setDbId(Long dbId) {
        this.dbId = dbId;
    }
}