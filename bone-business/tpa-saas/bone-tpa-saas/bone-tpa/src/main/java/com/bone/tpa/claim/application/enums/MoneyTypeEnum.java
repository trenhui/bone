package com.bone.tpa.claim.application.enums;

import lombok.Getter;

/**
 * 发票的钱类型枚举
 */
@Getter
public enum MoneyTypeEnum {

    NO_POOLING_AMOUNT("noPoolingAmount", "发票总金额-统筹"),

    TOTAL_AMOUNT("totalAmount", "发票总金额"),

    CUSTOM("custom", "自定义"),

    ;

    private final String code;
    private final String value;

    MoneyTypeEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static MoneyTypeEnum getByCode(String code) {
        for (MoneyTypeEnum e : MoneyTypeEnum.values()) {
            if (code.equals(e.getCode())) {
                return e;
            }
        }
        return null;
    }

}
