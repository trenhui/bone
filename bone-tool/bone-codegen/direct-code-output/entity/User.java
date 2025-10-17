package com.example.entity;

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
    private String phone;
    private Integer status;
    private Long createTime;
    private Long updateTime;
}
