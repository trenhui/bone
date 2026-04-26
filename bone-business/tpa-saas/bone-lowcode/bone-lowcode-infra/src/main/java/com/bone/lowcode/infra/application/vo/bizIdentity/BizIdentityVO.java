package com.bone.lowcode.infra.application.vo.bizIdentity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BizIdentityVO {

    //业务主体类型，1：保险公司，2：保险公司分公司，3：投保公司，4： 保单号
    private Byte bizType;

    private String bizName;

    private String bizCode;

    private String appCode;
}
