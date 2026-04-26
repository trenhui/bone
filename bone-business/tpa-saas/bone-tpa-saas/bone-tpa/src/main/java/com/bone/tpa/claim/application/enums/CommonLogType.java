package com.bone.tpa.claim.application.enums;

import lombok.Getter;

@Getter
public enum CommonLogType {
    FROM_TPA_LOG("FROM_TPA_LOG", "从tpa同步"),

    TO_TPA_LOG("TO_TPA_LOG", "往tpa推送"),
    CLAIM_COMMON("CLAIM_COMMON", "赔案普通日志"),
    OCR_API_DATA("OCR_API_DATA", "ocr识别数据"),

    FLOW_LOG("FLOW_LOG", "流程日志"),
    ;
    private String code ;
    private String desc ;

    CommonLogType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
