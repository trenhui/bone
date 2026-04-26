package com.bone.tpa.sdk.claim.enums;

public enum InvoiceUpdateFieldEnum {

    RESPONSIBILITY("RESPONSIBILITY", "责任信息"),

    HOSPITAL("HOSPITAL", "医院信息"),

    DISEASE("DISEASE", "疾病信息"),

    ;

    private final String code;
    private final String value;

    InvoiceUpdateFieldEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static InvoiceUpdateFieldEnum getByCode(String code) {
        for (InvoiceUpdateFieldEnum e : InvoiceUpdateFieldEnum.values()) {
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
