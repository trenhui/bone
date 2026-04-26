package com.bone.tpa.facade.vo;

import lombok.Data;

@Data
public class PageBizIdentityVO {

    private String bizName ;
    private String bizCode ;
    //业务主体类型，1：保险公司，2：保险公司分公司，3：投保公司，4： 保单号
    private Integer bizType;
    private String appCode;
}
