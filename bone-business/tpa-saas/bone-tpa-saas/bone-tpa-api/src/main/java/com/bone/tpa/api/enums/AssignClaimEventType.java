package com.bone.tpa.api.enums;

import lombok.Getter;

@Getter
public enum AssignClaimEventType {
    /**
     * tpa分配人员和组，并且同步给saas状态变成初审中
     */
    tpa分配初审人员和组("80",3,"saas", "tpa分配初审人员和组"),

    /**
     * tpa只分配组和人员，并且同步给saas变更为录入中
     */
    tpa分配普康的录入人员和组("82",3,"saas", "tpa分配普康的录入人员和组"),
    /**
     *tpa只分配组和人员，并且同步给saas变更为质检中
     */
    tpa分配质检人员和组("83",3,"saas", "tpa分配质检人员和组"),

    tpa手工分配审核人员("84",3,"saas","tpa手工分配审核人员"),


    tpa手工分配复核人员("85",3,"saas","tpa手工分配复核人员"),


    ;
    private String code ;
    /**
     * 因为三个接口共用一个eventType枚举，所以同type做区分
     * 0 待定，tpa自己去梳理
     * 1 同步赔案
     * 2 退回赔案
     * 3 tpa分配赔案和组
     *
     */
    private Integer type ;
    /**
     * 接收事件系统名
     */
    private String system;
    private String desc;

    AssignClaimEventType(String code,Integer type,String system, String desc) {
        this.code = code;
        this.type = type;
        this.system= system;
        this.desc = desc;
    }

    public static AssignClaimEventType getByCode(String code) {
        for (AssignClaimEventType assignClaimEventType : AssignClaimEventType.values()) {
            if (assignClaimEventType.getCode().equals(code)) {
                return assignClaimEventType;
            }
        }
        return null;
    }

}
