package com.bone.tpa.api.enums;

import lombok.Getter;

@Getter
public enum YesOrNoEnum {
    YES(1, "是"),
    NO(0, "否"),
    ;

    private Integer code;
    private String desc;
    YesOrNoEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static YesOrNoEnum getByCode(Integer code) {
        for (YesOrNoEnum yesOrNoEnum : YesOrNoEnum.values()) {
            if (yesOrNoEnum.getCode().equals(code)) {
                return yesOrNoEnum;
            }
        }
        return null;
    }
}
