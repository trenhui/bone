package com.bone.metadata.sdk.query.dsl.support;

import com.bone.metadata.sdk.domain.exception.SDKException;

/**
 * 查询异常 - 当DSL查询过程中发生错误时抛出
 */
public class QueryException extends SDKException {

    public QueryException(String message) {
        super("QUERY_ERROR", message);
    }

    public QueryException(String message, Throwable cause) {
        super("QUERY_ERROR", message, cause);
    }

    public QueryException(Throwable cause) {
        super("QUERY_ERROR", cause);
    }
}