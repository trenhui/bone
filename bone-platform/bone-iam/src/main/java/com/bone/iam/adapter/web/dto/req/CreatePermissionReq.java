package com.bone.iam.adapter.web.dto.req;

import com.bone.iam.domain.model.permission.vo.PermissionType;
import lombok.Data;

@Data
public class CreatePermissionReq {
    private String code;
    private String name;
    private String description;
    private Long parentId;
    private PermissionType type;
}