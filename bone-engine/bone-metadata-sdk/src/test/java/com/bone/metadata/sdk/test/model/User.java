package com.bone.metadata.sdk.test.model;

import lombok.Data;

/**
 * 用户实体类
 */
@Data
public class User {
    private Long id;
    private String username;
    private String password;
    private String email;
    private Long roleId;
    private Integer age;
    private Integer status;
}