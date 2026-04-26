package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

/**
 * 次日种类定义
 */
@Getter
public enum TimesLimitDefinitionEnum {

    CLAIM("CLAIM", "一赔案记一次"),

    DAY("DAY", "发票同日记一次"),

    HOSPITAL("HOSPITAL", "发票同日同院记一次"),

    DEPARTMENT("DEPARTMENT", "发票同日同院同科室记一次"),

    DISEASE("DISEASE", "发票同日同院同病记一次"),



    CLAIM_SAME_DAY("CLAIM_SAME_DAY", "按同日赔案累计"),

    INVOICE_SAME_DAY("INVOICE_SAME_DAY", "按同日发票累计"),



    CLAIM_SAME_MONTH("CLAIM_SAME_MONTH", "按同月赔案累计"),

    INVOICE_SAME_MONTH("INVOICE_SAME_MONTH", "按同月发票累计"),

    ;

    private final String code;
    private final String value;

    TimesLimitDefinitionEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static TimesLimitDefinitionEnum getByCode(String code) {
        for (TimesLimitDefinitionEnum e : TimesLimitDefinitionEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
