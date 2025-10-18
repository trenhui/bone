package com.bone.metadata.sdk.test.model;

import com.bone.metadata.sdk.Entity;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.core.annotation.Id;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Table("users")
public class User implements Entity<Long> {
    @Id
    private Long id;
    private String username;
    private String password;
    private String email;
    private Long roleId;
    private Integer age;
    private Integer status;

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public void setId(Long id) {
        this.id = id;
    }
}