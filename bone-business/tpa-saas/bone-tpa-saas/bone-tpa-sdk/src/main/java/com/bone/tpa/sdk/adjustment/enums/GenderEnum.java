package com.bone.tpa.sdk.adjustment.enums;

import lombok.Getter;

public enum GenderEnum {
    MALE("0", "男"),
    FEMALE("1", "女"),

    ;
    private String code ;
    private String desc;

    GenderEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static GenderEnum getEnumByCode(String code) {
        for (GenderEnum genderEnum : GenderEnum.values()) {
            if (genderEnum.getCode().equals(code)) {
                return genderEnum;
            }
        }
        return null;
    }

    public static GenderEnum getEnumByDesc(String desc) {
        for (GenderEnum genderEnum : GenderEnum.values()) {
            if (genderEnum.getDesc().equals(desc)) {
                return genderEnum;
            }
        }
        return null;
    }
}
