package com.bone.tpa.api.enums;

import lombok.Getter;

/**
 * 医保费用类型
 * 甲类/乙类/丙类
 */
@Getter
public enum MedicalCostTypeEnum {
    甲类("0","甲类"),
    乙类("1","乙类"),
    丙类("2","丙类"),
    ;
    private String code ;
    private String desc;
    MedicalCostTypeEnum(String code,String desc){
        this.code = code;
        this.desc = desc;
    }

    public static MedicalCostTypeEnum getByDesc(String desc){
        for(MedicalCostTypeEnum type : MedicalCostTypeEnum.values()){
            if(type.getDesc().equals(desc)){
                return type;
            }
        }
        return null;
    }

    public static MedicalCostTypeEnum getByCode(String code){
        for(MedicalCostTypeEnum type : MedicalCostTypeEnum.values()){
            if(type.getCode().equals(code)){
                return type;
            }
        }
        return null;
    }
}
