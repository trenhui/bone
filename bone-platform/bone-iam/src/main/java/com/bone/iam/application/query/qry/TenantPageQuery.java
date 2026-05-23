package com.bone.iam.application.query.qry;

import lombok.Data;

@Data
public class TenantPageQuery {
    private Integer page = 1;
    private Integer size = 10;
    private String code;
    private String name;
}
