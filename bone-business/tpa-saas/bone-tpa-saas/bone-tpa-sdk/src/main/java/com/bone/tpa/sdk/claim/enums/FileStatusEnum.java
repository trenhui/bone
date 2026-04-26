package com.bone.tpa.sdk.claim.enums;

public enum FileStatusEnum {
    /**
     * 代表数据文件尚未解析
     */
    EXCEL_WAITING("EXCEL_WAITING", "文件等待提交"),

    /**
     * 代表数据文件已经解析
     */
    EXCEL_COMPLETE("EXCEL_COMPLETE", "文件提交成功"),

    /**
     * 代表有问题
     */
    EXCEL_ERROR("EXCEL_ERROR", "文件提交失败"),


    /**
     * 影像件正在解析
     */
    IMAGE_WAITING("IMAGE_WAITING", "影像件正在解析"),

    /**
     * 影像件解析完成
     */
    IMAGE_COMPLETE("IMAGE_COMPLETE", "影像件解析完成"),

    /**
     * 影像件解析失败
     */
    IMAGE_ERROR("IMAGE_ERROR", "影像件解析失败"),

            ;

    private final String code;
    private final String value;

    FileStatusEnum(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public static FileStatusEnum getByCode(String code) {
        for (FileStatusEnum e : FileStatusEnum.values()) {
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
