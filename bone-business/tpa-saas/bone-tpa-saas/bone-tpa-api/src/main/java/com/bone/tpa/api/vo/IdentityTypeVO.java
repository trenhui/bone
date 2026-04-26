package com.bone.tpa.api.vo;

import lombok.Data;

@Data
public class IdentityTypeVO {
    /**
     * 业务主体类型，1：保险公司，2：保险公司分公司，3：投保公司，4：保险公司分公司（保单号）
     */
    private Integer bizType ;
    private String bizIdentityCode;
}
