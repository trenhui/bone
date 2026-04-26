package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

/**
 * 后付责任类型相关枚举
 */
@Getter
public enum NextLiabilityTypeEnum {

    EXCEEDING_PAY("EXCEEDING_PAY", "先付责任比例外也赔"),

    EXCEEDING_NO_PAY("EXCEEDING_NO_PAY", "先付责任比例外不赔"),

    PRIOR_ZERO_PAY("PRIOR_ZERO_PAY", "先付责任为0才赔");

    ;

    private final String code;
    private final String value;

    NextLiabilityTypeEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static NextLiabilityTypeEnum getByCode(String code) {
        for (NextLiabilityTypeEnum e : NextLiabilityTypeEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
