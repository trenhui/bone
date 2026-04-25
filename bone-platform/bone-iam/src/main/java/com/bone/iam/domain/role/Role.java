package com.bone.iam.domain.role;

import com.bone.core.domain.AggregateRoot;
import com.bone.iam.domain.role.event.RoleCreatedEvent;
import com.bone.metadata.sdk.domain.annotation.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("iam_role")
public class Role extends AggregateRoot<Long> {
    private Long id;
    private Long tenantId;
    private String name;
    private String code;
    private int type;
    private String description;
    private Long parentRoleId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static Role create(String name, String code, String description, int type, Long tenantId, Long parentRoleId) {
        Role role = new Role();
        role.name = name;
        role.code = code;
        role.description = description;
        role.type = type;
        role.tenantId = tenantId;
        role.parentRoleId = parentRoleId;
        role.createTime = LocalDateTime.now();
        role.updateTime = LocalDateTime.now();
        role.addDomainEvent(new RoleCreatedEvent(role));
        return role;
    }

    public void update(String description) {
        this.description = description;
        this.updateTime = LocalDateTime.now();
    }
}
