package com.bone.core.exception;

import java.io.Serial;

/**
 * 系统异常：表示系统内部错误，如数据库连接失败、IO异常等
 */
public class SystemException extends BizException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SystemException(String message, Throwable cause) {
        super(message, cause);
    }

    public SystemException(String message) {
        super(message);
    }

    public static SystemException of(String message) {
        return new SystemException(message, null);
    }

    public static SystemException of(String message, Throwable cause) {
        return new SystemException(message, cause);
    }
}
