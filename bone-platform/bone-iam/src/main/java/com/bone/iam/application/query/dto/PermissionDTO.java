package com.bone.iam.application.query.dto;

import com.bone.iam.domain.model.permission.vo.PermissionType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PermissionDTO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Long parentId;
    private PermissionType type;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}