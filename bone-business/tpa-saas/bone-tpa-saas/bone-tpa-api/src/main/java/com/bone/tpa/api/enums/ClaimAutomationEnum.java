package com.bone.tpa.api.enums;

import lombok.Getter;

//1：自动化作业，2：非自动化作业
@Getter
public enum ClaimAutomationEnum {

    AUTOMATION(1, "自动化作业"),
    NON_AUTOMATION(2, "非自动化作业")
    ;
    private Integer code ;
    private String desc ;

    ClaimAutomationEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ClaimAutomationEnum getByCode(Integer code) {
        for (ClaimAutomationEnum type : ClaimAutomationEnum.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

}
