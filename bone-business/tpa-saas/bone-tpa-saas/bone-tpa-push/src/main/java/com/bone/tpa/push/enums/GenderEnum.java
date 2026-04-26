package com.bone.tpa.push.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.util.StringUtils;

@Getter
@AllArgsConstructor
public enum GenderEnum {
    MALE("0", "1", "男"),
    FEMALE("1", "2", "女"),

    ;
    private String code;
    private String insureCode;
    private String desc;

    public static GenderEnum getEnumByCode(String code) {
        for (GenderEnum genderEnum : GenderEnum.values()) {
            if (genderEnum.getCode().equals(code)) {
                return genderEnum;
            }
        }
        return null;
    }

    public static String getDescByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }

        for (GenderEnum item : GenderEnum.values()) {
            if (item.getCode().equals(code)) {
                return item.getDesc();
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
