package com.bone.blueprint.domain.model.iam;

import com.bone.core.domain.AggregateRoot;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Role extends AggregateRoot<Long> {
    private String name;
    private String description;
    private Long tenantId;
    
    public static Role create(String name, String description, Long tenantId) {
        Role role = new Role();
        role.name = name;
        role.description = description;
        role.tenantId = tenantId;
        return role;
    }
    
    public void update(String name, String description) {
        this.name = name;
        this.description = description;
    }
    
    // 仅供 SDK 回填 ID 使用
    void setId(Long id) {
        super.setId(id);
    }
}
