package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

@Getter
public enum TimeUnit {

    YEARS("YEARS", "计算日期精确到年"),

    MONTHS("MONTHS", "计算日期精确到月"),

    DAYS("DAYS", "计算日期精确到日"),

            ;

    private final String code;
    private final String value;

    TimeUnit(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static TimeUnit getByCode(String code) {
        for (TimeUnit e : TimeUnit.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
