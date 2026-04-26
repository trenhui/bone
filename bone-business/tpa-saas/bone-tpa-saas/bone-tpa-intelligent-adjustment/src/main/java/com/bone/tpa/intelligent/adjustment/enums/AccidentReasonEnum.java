package com.bone.tpa.intelligent.adjustment.enums;

import lombok.Getter;

/**
 * 出险原因枚举
 */
@Getter
public enum AccidentReasonEnum {

    Jb("疾病",1),
    Yw("意外",2),
    Txyw("特殊意外",3),
    Sy("生育",4),
    Tj("体检/疫苗接种",5),
    Tsjb("特殊疾病",6);

    private String name;
    private Integer code;

    private AccidentReasonEnum(String name, Integer code) {
        this.name = name;
        this.code = code;
    }

    public static AccidentReasonEnum getByCode(String code) {
        for (AccidentReasonEnum e : AccidentReasonEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
