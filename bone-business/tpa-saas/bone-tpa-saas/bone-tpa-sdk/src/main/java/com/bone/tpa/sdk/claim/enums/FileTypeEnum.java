package com.bone.tpa.sdk.claim.enums;

public enum FileTypeEnum {
    EXCEL("EXCEL", "数据文件", (byte) 1),

    IMAGE("IMAGE", "影像件", (byte) 2),

    PICTURE("PICTURE", "图片", (byte) 3),

    ATTACHMENT("ATTACHMENT", "附件文件", (byte) 4),

    ;

    private final String code;
    private final String value;
    private final Byte configCode;

    FileTypeEnum(String code, String value, Byte configCode) {
        this.code = code;
        this.value = value;
        this.configCode = configCode;
    }

    public static FileTypeEnum getByCode(String code) {
        for (FileTypeEnum e : FileTypeEnum.values()) {
            if (e.getCode().equals(code)) {
                return e;
            }
        }
        return null;
    }

    public static FileTypeEnum getByConfigCode(Byte configCode) {
        for (FileTypeEnum e : FileTypeEnum.values()) {
            if (e.getConfigCode().equals(configCode)) {
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

    public Byte getConfigCode() {
        return configCode;
    }
}
