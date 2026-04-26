package com.bone.tpa.sdk.claim.enums;


public enum BizErrorCode {

    BAD_REQUEST(400, "请求参数不正确"),
    UNAUTHORIZED(401, "账号未登录"),
    FORBIDDEN(403, "没有该操作权限: %s"),
    NOT_FOUND(404, "请求未找到"),
    METHOD_NOT_ALLOWED(405, "请求方法不正确"),
    LOCKED(423, "请求失败，请稍后重试"),// 并发请求，不允许
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试"),

    // ========== 服务端错误段 ==========

    INTERNAL_SERVER_ERROR(500, "系统异常"),
    NOT_IMPLEMENTED(501, "功能未实现/未开启"),


    NO_RECORD(502, "表数据缺失: %s"),

    PARAMETER_ERROR(503, "外部参数错误: %s"),

    INNER_PARAMETER_ERROR(504, "内部参数错误: %s"),

    PROJECT_MONEY_TOO_MUCH(505, "项目概况金额大于发票总金额: %s"),

    ITEM_MONEY_TOO_MUCH(506, "项目明细金额大于项目概况金额: %s"),

    DURING_ADJUSTMENT(510, "正在理算中: %s"),

    ADJUSTMENT_ERROR(511, "发生理算错误: %s"),

    CLAIM_HANG_UP(512, "赔案已挂起: %s"),

    STATUS_ERROR(513, "表状态错误: %s"),

    OUTER_CLIENT_ERROR(514, "%s"),

    IMPORT_CONFIG_ERROR(515, "文件上传错误: %s"),

    CREATE_CLAIM_ERROR(516, "赔案创建失败"),

    BIZ_STAGE_ERROR(517, "业务阶段错误: %s"),

    BIZ_MODEL_ERROR(518, "业务模型错误: %s"),

    SIGN_CONFIRM_ERROR(519, "%s"),

    ADJUST_NOT_ALLOWED(520, "该发票不能理算: %s"),

    DATA_UNCHANGEABLE(521, "不可变动"),

    IMAGE_NOT_CLASSIFIED(522, "分类错误：%s"),

    CLASSIFY_CODE_ERROR(523, "该分类不存在: %s"),

    NO_DELETE_ALL(524, "不能删除该表格全部记录!"),

    COPY_NOY_ALLOWED(525, "赔案不能复制：%s"),


    // ========== 自定义错误段 ==========
    REPEATED_REQUESTS(900, "重复请求，请稍后重试"), // 重复请求

    UNKNOWN_ERROR(999, "发生异常:%s")

    ;

    //错误码
    private final Integer code;

    //错误描述
    private final String description;


    BizErrorCode(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public Integer getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
