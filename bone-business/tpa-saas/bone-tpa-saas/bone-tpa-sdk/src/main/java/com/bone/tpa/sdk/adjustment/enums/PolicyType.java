package com.bone.tpa.sdk.adjustment.enums;

public enum PolicyType {
    GROUP(2, "团险"),
    PERSON(1, "个险"),
    ;

    PolicyType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private Integer code;

    private String desc;

    static public PolicyType getByCode(Integer code){
        for (PolicyType status : PolicyType.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    static public PolicyType getByDesc(String desc){
        for (PolicyType status : PolicyType.values()) {
            if (status.getDesc().equals(desc)) {
                return status;
            }
        }
        return null;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
