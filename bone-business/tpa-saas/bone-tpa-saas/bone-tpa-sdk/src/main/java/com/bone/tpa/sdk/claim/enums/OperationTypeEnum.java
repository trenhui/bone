package com.bone.tpa.sdk.claim.enums;

public enum OperationTypeEnum {

    CREATE("CREATE", "创建"),

    UPDATE("UPDATE", "更新"),

    DELETE("DELETE", "删除"),

    DUPLICATE("DUPLICATE", "复制"),

    HANGUP("HANGUP", "挂起"),


    SIGN("SIGN", "签收"),
    JUMP_OVER_PRECHECK("JUMP_OVER_PRECHECK", "跳过初审"),
    JUMP_OVER_INPUT("JUMP_OVER_INPUT", "跳过录入"),
    JUMP_OVER_QUALITY_CHECK("JUMP_OVER_QUALITY_CHECK", "跳过质检"),

    PRE_CHECK_ASSIGN("PRE_CHECK_ASSIGN", "初审设置操作人"),
    PRE_CHECK_MANUAL_COMPLETE("PRE_CHECK_MANUAL_COMPLETE", "人工初审完成"),


    RELEASE_HANGUP("RELEASE_HANGUP", "解挂"),


    INPUT_CHECK_ASSIGN("INPUT_CHECK_ASSIGN", "录入阶段设置操作人"),
    INPUT_MANUAL_COMPELTE("INPUT_MANUAL_COMPELTE", "人工录入完成"),
    WAIBAO_INPUT_COMPELTE("WAIBAO_INPUT_COMPELTE", "外包录入"),
    INPUT_OCR("INPUT_OCR", "OCR录入"),


    QUALITY_CHECK_ASSIGN("QUALITY_CHECK_ASSIGN", "质检阶段设置操作人"),
    QUALITY_CHECK_COMPLETE("QUALITY_CHECK_COMPLETE", "质检完成"),

    APPROVE_AUTO_COMPLETE("APPROVE_AUTO_COMPLETE", "自动审核完成"),


    APPROVE_MANUAL_COMPLETE("APPROVE_MANUAL_COMPLETE", "人工审核完成"),


    REVIEW_CHECK_ASSIGN("REVIEW_CHECK_ASSIGN", "复核阶段设置操作人"),
    REVIEW_COMPLETE("REVIEW_COMPLETE", "复核完成"),




    PRECHECK_AUTO("PRECHECK_AUTO", "自动初审"),

    BACK_NODE("BACK_NODE", "退回节点"),

    COPY_CLAIM("COPY_CLAIM", "复制赔案"),

    APPROVE_AUTO("APPROVE_AUTO", "自动审核"),
    REJECT("REJECT", "驳回"),
    RETURN_MANUAL("RETURN_MANUAL", "退回人工处理"),


    REGIST("REGIST", "报案"),

    REGIST_FAIL("REGIST_FAIL", "报案失败"),

    COPY_INVOICE("COPY_INVOICE", "复制发票"),

    UPDATE_LIMIT_HOUR("UPDATE_LIMIT_HOUR", "更新时效"),

    CREATE_CERTIFICATE("CREATE_CERTIFICATE", "生成单证"),

    ;

    private final String code;
    private final String value;

    OperationTypeEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static OperationTypeEnum getByCode(String code) {
        for (OperationTypeEnum e : OperationTypeEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }

    public String getCode() {
        return code;
    }

    public String getValue() {
        return value;
    }
}
