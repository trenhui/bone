package com.bone.metadata.sdk.query.dsl;

/**
 * 连接类型枚举，用于SQL JOIN操作
 */
public enum JoinType {
    /** 内连接 */
    INNER,
    /** 左连接 */
    LEFT,
    /** 右连接 */
    RIGHT,
    /** 全连接 */
    FULL
}