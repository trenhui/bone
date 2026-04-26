package com.bone.tpa.sdk.adjustment.enums;

import lombok.Getter;

/**
 * 津贴天数计算方式枚举
 */
@Getter
public enum DateRangeEnum {
    /**
     * 包含起止日期（如 2023-10-01 至 2023-10-02 算2天）
     */
    NATURAL("NATURAL", "自然日计算法"),
    /**
     * 自然日差（如 2023-10-01 至 2023-10-02 算1天）
     */
    CALENDER("CALENDER", "日历日计算法"),
    /**
     * 精确24小时为一天（如 25小时算1天，23小时算0天）
     */
    TWENTY_FOUR_HOUR("TWENTY_FOUR_HOUR", "24小时"),

    ;

    private final String code;
    private final String value;

    DateRangeEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static DateRangeEnum getByCode(String code) {
        for (DateRangeEnum e : DateRangeEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
