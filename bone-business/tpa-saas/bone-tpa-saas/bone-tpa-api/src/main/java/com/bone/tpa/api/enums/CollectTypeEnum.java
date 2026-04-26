package com.bone.tpa.api.enums;

import lombok.Getter;

@Getter
public enum CollectTypeEnum {
    PERSON("person","个人","对私"),
    COMPANY("company","企业","对公")
    ;
    private String code ;
    private String desc;
    private String transferMethod;
    CollectTypeEnum(String code,String desc,String transferMethod){
        this.code = code;
        this.desc = desc;
        this.transferMethod = transferMethod;
    }
    public static CollectTypeEnum getEnumByCode(String code){
        for(CollectTypeEnum collectTypeEnum : CollectTypeEnum.values()){
            if(collectTypeEnum.getCode().equals(code)){
                return collectTypeEnum;
            }
        }
        return null;
    }

    public static CollectTypeEnum getEnumByDesc(String desc){
        for(CollectTypeEnum collectTypeEnum : CollectTypeEnum.values()){
            if(collectTypeEnum.getDesc().equals(desc)){
                return collectTypeEnum;
            }
        }
        return null;
    }

    public static CollectTypeEnum getEnumByTransfer(String transferMethod){
        for(CollectTypeEnum collectTypeEnum : CollectTypeEnum.values()){
            if(collectTypeEnum.getTransferMethod().equals(transferMethod)){
                return collectTypeEnum;
            }
        }
        return null;
    }
}
