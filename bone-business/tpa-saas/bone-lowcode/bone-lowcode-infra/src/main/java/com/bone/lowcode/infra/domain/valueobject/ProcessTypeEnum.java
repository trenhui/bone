package com.bone.lowcode.infra.domain.valueobject;

import lombok.AllArgsConstructor;
import lombok.Getter;

//tpa流程枚举
@AllArgsConstructor
@Getter
public enum ProcessTypeEnum {

    SIGN((byte) 1, "签收"),
    FIRST_AUDIT((byte) 2, "初审"),
    ENTRY((byte) 3, "录入"),
    QUALITY_CHECK((byte) 4, "质检"),
    AUDIT((byte) 5, "审核"),
    REVIEW((byte) 6, "复核"),
    ;

    private final byte code;

    private final String desc;
}
