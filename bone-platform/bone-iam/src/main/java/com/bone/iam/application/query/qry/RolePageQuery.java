package com.bone.iam.application.query.qry;

import com.bone.core.model.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class RolePageQuery extends PageParam {
    private String keyword;
    private Long tenantId;
}