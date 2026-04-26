package com.bone.tpa.api.enums;

import lombok.Getter;

@Getter
public enum CollectTransferType {
    PERSON("person","对私"),
    COMPANY("company","对公")
    ;
    private String code ;
    private String desc;
    CollectTransferType(String code,String desc){
        this.code = code;
        this.desc = desc;
    }
    public static CollectTransferType getEnumByCode(String code){
        for(CollectTransferType collectTypeEnum : CollectTransferType.values()){
            if(collectTypeEnum.getCode().equals(code)){
                return collectTypeEnum;
            }
        }
        return null;
    }

}
