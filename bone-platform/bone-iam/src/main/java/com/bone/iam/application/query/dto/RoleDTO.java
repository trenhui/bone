package com.bone.iam.application.query.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoleDTO {
    private Long id;
    private String name;
    private String description;
    private Long tenantId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}