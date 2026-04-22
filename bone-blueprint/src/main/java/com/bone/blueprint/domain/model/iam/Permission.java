package com.bone.blueprint.domain.model.iam;

import com.bone.core.domain.AggregateRoot;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Permission extends AggregateRoot<Long> {
    private String code;
    private String name;
    private String description;
    private Long parentId;
    private String type;
    
    public static Permission create(String code, String name, String description, Long parentId, String type) {
        Permission permission = new Permission();
        permission.code = code;
        permission.name = name;
        permission.description = description;
        permission.parentId = parentId;
        permission.type = type;
        return permission;
    }
    
    public void update(String name, String description, Long parentId, String type) {
        this.name = name;
        this.description = description;
        this.parentId = parentId;
        this.type = type;
    }
    
    // 仅供 SDK 回填 ID 使用
    void setId(Long id) {
        super.setId(id);
    }
}
