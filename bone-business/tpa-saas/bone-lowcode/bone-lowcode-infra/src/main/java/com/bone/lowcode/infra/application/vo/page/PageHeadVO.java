package com.bone.lowcode.infra.application.vo.page;

import lombok.Data;

@Data
public class PageHeadVO {

    private String id;

    private String code;

    // 业务身份编码
    private String bizIdentityCode;

    // 配置业务字段开关 0-关；1-开
    private Byte businessFieldEnabled;
}
