package com.bone.tpa.sdk.adjustment.enums;

import lombok.Getter;

/**
 * 理算明细失败原因枚举
 */
public enum AdjustmentDetailFailReasonEnum {

    ONE("发票就诊日期不在保单的有效期内","1"),
    TWO("发票的姓名与出险人不一致","2"),
    THREE("就诊的医院不符合保险医院规则","3"),
    FOUR("诊断不符合保险的责免规则","4"),
    FIVE("发票计算合理金额为0","5"),
    SIX("未达免赔的导致合理金额为0","6"),
    SEVEN("未达免赔的导致合理金额为0","7"),
    EIGHT("强制使用医保的责任，未使用医保卡进行就诊","8"),
    NINE("无剩余保额","9"),
    TEN("发票类就诊类型不符合责任定义","10"),
    ELEVEN("责任不包含特需区域就诊","11"),
    TWELVE("不满足等待期","12"),
    THIRTEEN("当前药店票据暂不支持理赔","13"),   //产品说暂时无视这个规则

    ;

    private String desc;
    private String code;

    AdjustmentDetailFailReasonEnum(String desc, String code) {
        this.desc = desc;
        this.code = code;
    }

    public static AdjustmentDetailFailReasonEnum getEnumByCode(String code) {
        for (AdjustmentDetailFailReasonEnum obj : AdjustmentDetailFailReasonEnum.values()) {
            if (obj.getCode().equals(code)) {
                return obj;
            }
        }
        return null;
    }

    public static AdjustmentDetailFailReasonEnum getEnumByDesc(String desc) {
        for (AdjustmentDetailFailReasonEnum obj : AdjustmentDetailFailReasonEnum.values()) {
            if (obj.getDesc().equals(desc)) {
                return obj;
            }
        }
        return null;
    }

    public String getDesc() {
        return desc;
    }

    public String getCode() {
        return code;
    }
}
