package com.bone.iam.application.query.qry;

import com.bone.core.model.PageParam;
import com.bone.iam.domain.permission.vo.PermissionType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class PermissionPageQuery extends PageParam {
    private String keyword;
    private PermissionType type;
    private Long parentId;
}