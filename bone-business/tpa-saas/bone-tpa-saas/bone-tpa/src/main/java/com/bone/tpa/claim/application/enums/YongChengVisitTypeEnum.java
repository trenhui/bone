package com.bone.tpa.claim.application.enums;

import lombok.Getter;

/**
 * 永诚专用
 *
 * 当就诊类型选定时，同步带出出险类型
 */
@Getter
public enum YongChengVisitTypeEnum {

    type1("药房", "药房购药"),
    type2("门/急诊", "门/急诊"),
    type3("住院", "住院"),
    type4("其他", "特定疾病"),
    type5("门诊-慢特病", "特定疾病"),

    ;

    private final String visitTypeCn;
    private final String exAccidenttypeCn;

    YongChengVisitTypeEnum(String visitTypeCn, String exAccidenttypeCn) {
        this.visitTypeCn = visitTypeCn;
        this.exAccidenttypeCn = exAccidenttypeCn;
    }
}
