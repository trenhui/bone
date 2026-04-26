package com.bone.tpa.sdk.claim.enums;


import lombok.Getter;

/**
 * ("其他",1),
 * ("资料不齐（挂给客户）",4),
 * ("资料不齐（暂存）",6),
 * ("无承保信息-主被未承保",7),
 * ("无承保信息-家属未承保",8),
 * ("保单选择有误",9),
 * ("公账额度不足",10),
 * ("规则暂不明确",11),
 */
@Getter
public enum TpaHangupReason {
    其他("1","其他"),
    资料不齐挂给客户("4","资料不齐（挂给客户）"),
    资料不齐暂存("6","资料不齐（暂存）"),
    无承保信息主被未承保("7","无承保信息-主被未承保"),
    无承保信息家属未承保("8","无承保信息-家属未承保"),
    保单选择有误("9","保单选择有误"),
    公账额度不足("10","公账额度不足"),
    规则暂不明确("11","规则暂不明确");


    ;
    private String code ;
    private String desc;

    TpaHangupReason(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static TpaHangupReason getByCode(String code) {
        for (TpaHangupReason e : TpaHangupReason.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }
}
