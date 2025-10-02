package com.bone.metadata.sdk.test.domain;

import com.bone.core.annotation.Table;
import com.bone.core.domain.entity.Entity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
@Table("roles")
public class Role extends Entity<Long> {
    private String roleName;
    private String description;

    // Fixed constructor with correct parameter types
    public Role(Long id, String roleName, String description) {
        super(id);
        this.roleName = roleName;
        this.description = description;
    }
}

