package com.bone.metadata.sdk.query.dsl.support;

/**
 * 查询异常 - 当DSL查询过程中发生错误时抛出
 */
public class QueryException extends RuntimeException {

    public QueryException(String message) {
        super(message);
    }

    public QueryException(String message, Throwable cause) {
        super(message, cause);
    }

    public QueryException(Throwable cause) {
        super(cause);
    }
}