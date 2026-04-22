package com.bone.iam.application.command.cmd;

import com.bone.iam.domain.model.permission.vo.PermissionType;
import lombok.Data;

@Data
public class CreatePermissionCmd {
    private String code;
    private String name;
    private String description;
    private Long parentId;
    private PermissionType type;
}