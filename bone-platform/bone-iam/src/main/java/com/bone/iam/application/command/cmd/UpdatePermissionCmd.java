package com.bone.iam.application.command.cmd;

import com.bone.iam.domain.permission.vo.PermissionType;
import lombok.Data;

@Data
public class UpdatePermissionCmd {
    private Long id;
    private String name;
    private String description;
    private String resourceType;
    private String resourcePath;
    private String action;
    private Long parentId;
    private PermissionType type;
    private Integer sortOrder;
}

