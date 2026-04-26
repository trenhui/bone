package com.bone.tpa.facade.enums;

import lombok.Getter;

@Getter
public enum BindTypeEnum {
    选项集("1", "选项集"),
    主数据("2", "主数据"),
    枚举("3", "枚举"),
    ;
    private String code;
    private String desc ;

    BindTypeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    public static BindTypeEnum getByCode(String code) {
        for (BindTypeEnum bindTypeEnum : BindTypeEnum.values()) {
            if (bindTypeEnum.getCode().equals(code)) {
                return bindTypeEnum;
            }
        }
        return null;
    }
}
