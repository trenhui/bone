package com.bone.lowcode.infra.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Getter;

//页面类型枚举
@AllArgsConstructor
@Getter
public enum PageTypeEnum {

    TEMPLATE((byte) 0, "模板"),
    BIZ_IDENTITY((byte) 1, "专属"),
    ;

    private final byte code;
    private final String desc;
}
