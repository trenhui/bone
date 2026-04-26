package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

/**
 * 责任免赔相关枚举
 */
@Getter
public enum DeductEnum {

    /**
     * 免赔方式
     */
    ABSOLUTE("ABSOLUTE", "绝对免赔"),

    RELATIVE("RELATIVE", "相对免赔"),


    /**
     * 免赔模式
     */
    UNIVERSAL("UNIVERSAL", "统一额度"),

    PERSONAL("PERSONAL", "个人免赔额度"),

    DIRECT("DIRECT", "直付免赔额度"),


    /**
     * 免赔形式
     */
    MONEY("MONEY", "免赔金额"),

    RATIO("RATIO", "免赔比例"),

    DAYS("DAYS", "免赔天数"),


    /**
     * 免赔周期
     */
    ANNUAL("ANNUAL", "按年度"),

    TIMES("TIMES", "按次数"),


    /**
     * 免赔对象枚举
     */
    ADJUST("ADJUST", "理算金额"),

    INVOICE("INVOICE", "发票费用"),


    /**
     * 免赔抵扣
     */
    MEDICAL_THIRD("MEDICAL_THIRD", "医保及三方可抵扣"),

    THIRD("THIRD", "医保不可抵扣，三方可抵扣"),

    NONE("NONE", "均不可抵扣"),


    ;

    private final String code;
    private final String value;

    DeductEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static DeductEnum getByCode(String code) {
        for (DeductEnum e : DeductEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
