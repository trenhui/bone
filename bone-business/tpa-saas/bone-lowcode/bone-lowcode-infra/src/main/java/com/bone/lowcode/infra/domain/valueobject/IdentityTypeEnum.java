package com.bone.lowcode.infra.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Getter;

//主体类型枚举
@AllArgsConstructor
@Getter
public enum IdentityTypeEnum {

    INSURANCE_COMPANY((byte) 1, "保险公司"),
    INSURANCE_COMPANY_BRANCH((byte) 2, "保险公司分公司"),
    INSURED_COMPANY((byte) 3, "投保公司"),
    POLICY((byte) 4, "保单"),
    ;

    private final byte code;
    private final String desc;
}
