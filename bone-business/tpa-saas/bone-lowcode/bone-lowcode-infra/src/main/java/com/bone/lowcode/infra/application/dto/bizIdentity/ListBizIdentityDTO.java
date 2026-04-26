package com.bone.lowcode.infra.application.dto.bizIdentity;

import lombok.Data;

@Data
public class ListBizIdentityDTO {

    private Long pageNum = 1L;

    private Long pageSize = 10L;

    //名称
    private String name;

    //业务主体类型，1：保险公司，2：保险公司分公司，3：投保公司，4：保险公司分公司（保单号）
    private Byte bizType;

    //是否启用，0：不启用，1：启用
    private Byte status;
}
