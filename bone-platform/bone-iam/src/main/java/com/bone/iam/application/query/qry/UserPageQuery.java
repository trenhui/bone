package com.bone.iam.application.query.qry;

import com.bone.core.model.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageQuery extends PageParam {
    private String keyword;
    private String status;
    private Long tenantId;
}