package com.bone.tool.codegen.domain.exception;

/**
 * 业务规则或应用约束失败时抛出，由适配层映射为 HTTP 响应。
 * 领域层与应用层均可使用，不依赖 Web 框架。
 */
public class CodegenBusinessException extends RuntimeException {

    private final int errorCode;

    public CodegenBusinessException(String message) {
        super(message);
        this.errorCode = 500;
    }

    public CodegenBusinessException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
