package com.bone.core.exception;

import com.bone.core.enums.ErrorCode;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;


/**
 * 业务异常：专门用于抛出明确的业务错误，如参数非法、状态冲突等
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BizException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 默认错误码，可根据需要自行调整
     */
    public static final int DEFAULT_ERROR_CODE = 500;

    /**
     * 业务错误码
     */
    private final int code;

    /**
     * 建议只从静态工厂方法调用
     */
    public BizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 建议只从静态工厂方法调用
     */
    public BizException(String message, Throwable cause) {
        super(message, cause);
        this.code = DEFAULT_ERROR_CODE;
    }

    /**
     * 建议只从静态工厂方法调用
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 建议只从静态工厂方法调用
     */
    public BizException(String message) {
        super(message);
        this.code = DEFAULT_ERROR_CODE;
    }


    // ===== 静态工厂方法区域 =====

    /**
     * 使用默认错误码 & 自定义错误消息
     */
    public static BizException of(String message) {
        return new BizException(DEFAULT_ERROR_CODE, message, null);
    }

    /**
     * 使用默认错误码 & 自定义错误消息 & 传递 cause
     */
    public static BizException of(String message, Throwable cause) {
        return new BizException(DEFAULT_ERROR_CODE, message, cause);
    }

    /**
     * 使用自定义错误码 & 错误消息
     */
    public static BizException of(int code, String message) {
        return new BizException(code, message, null);
    }

    /**
     * 使用自定义错误码 & 错误消息 & 传递 cause
     */
    public static BizException of(int code, String message, Throwable cause) {
        return new BizException(code, message, cause);
    }

    /**
     * 使用枚举类（推荐），如 ResultCode
     */
    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode.getCode(), errorCode.getMsg(), null);
    }

    /**
     * 使用枚举类并传递 cause
     */
    public static BizException of(ErrorCode errorCode, Throwable cause) {
        return new BizException(errorCode.getCode(), errorCode.getMsg(), cause);
    }
}
