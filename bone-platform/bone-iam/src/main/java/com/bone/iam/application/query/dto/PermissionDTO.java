package com.bone.iam.application.query.dto;

import com.bone.iam.domain.permission.vo.PermissionType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PermissionDTO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private PermissionType type;
    private String resourceType;
    private String resourcePath;
    private String action;
    private Long parentId;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PermissionDTO> children;
}
