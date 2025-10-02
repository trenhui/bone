package com.bone.metadata.sdk.test.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户-权限分页查询结果 DTO
 */
@Data
public class UserPermPageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String userName;

    private String roleName;

    private String permCode;

    private String permName;

    private String permPath;

    private String bizCode;

    private Integer permType;

    private Integer sortOrder;

    private LocalDateTime createTime;

    private Long createBy;

    private LocalDateTime updateTime;

    private Long updateBy;

    private Integer deleted;
}