package com.bone.iam.domain.model.role;

import com.bone.core.domain.AggregateRoot;
import com.bone.iam.domain.model.role.event.RoleCreatedEvent;
import com.bone.iam.domain.model.role.vo.RoleId;
import com.bone.iam.domain.model.role.vo.RoleName;
import com.bone.core.util.DistributedIdGenerator;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Role extends AggregateRoot<RoleId> {
    private RoleId id;
    private Long dbId;
    private RoleName name;
    private String description;
    private Long tenantId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public static Role create(RoleName name, String description, Long tenantId) {
        Role role = new Role();
        role.id = RoleId.of(DistributedIdGenerator.generateUuid());
        role.name = name;
        role.description = description;
        role.tenantId = tenantId;
        role.createTime = LocalDateTime.now();
        role.updateTime = LocalDateTime.now();
        role.addDomainEvent(new RoleCreatedEvent(role));
        return role;
    }

    public void update(String description) {
        this.description = description;
        this.updateTime = LocalDateTime.now();
    }

    void setDbId(Long dbId) {
        this.dbId = dbId;
    }
}