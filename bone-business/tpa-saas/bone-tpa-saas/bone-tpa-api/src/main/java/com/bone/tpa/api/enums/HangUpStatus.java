package com.bone.tpa.api.enums;

import lombok.Getter;

@Getter
public enum HangUpStatus {
    NO_HANG_UP("0", "未挂起"),
    HANG_UP("1", "已挂起"),
    CANCELED("2","已取消"),
    DEALT("3","已处理"),
    ;
    private String code ;

    private String desc ;

    HangUpStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
    public static HangUpStatus getByCode(String code){
        for (HangUpStatus hangUpStatus : HangUpStatus.values()) {
            if(hangUpStatus.getCode().equals(code)){
                return hangUpStatus;
            }
        }
        return null;
    }

    public static HangUpStatus getByDesc(String desc){
        for (HangUpStatus hangUpStatus : HangUpStatus.values()) {
            if(hangUpStatus.getDesc().equals(desc)){
                return hangUpStatus;
            }
        }
        return null;
    }


}
