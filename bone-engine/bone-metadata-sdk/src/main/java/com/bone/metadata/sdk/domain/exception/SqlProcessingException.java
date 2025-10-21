package com.bone.metadata.sdk.domain.exception;

/**
 * SQL处理异常
 * 当SQL语句处理过程中出现错误时抛出
 */
public class SqlProcessingException extends SDKException {
    private static final long serialVersionUID = 1L;
    
    // 处理阶段
    private String processingStage;
    // 原始SQL
    private String originalSql;
    
    /**
     * 创建SQL处理异常
     * @param message 异常消息
     */
    public SqlProcessingException(String message) {
        super("SQL_PROCESSING_ERROR", message);
    }
    
    /**
     * 创建SQL处理异常
     * @param message 异常消息
     * @param cause 根本原因
     */
    public SqlProcessingException(String message, Throwable cause) {
        super("SQL_PROCESSING_ERROR", message, cause);
    }
    
    /**
     * 设置处理阶段
     * @param stage 处理阶段
     * @return 当前异常实例
     */
    public SqlProcessingException setProcessingStage(String stage) {
        this.processingStage = stage;
        addContext("processingStage", stage);
        return this;
    }
    
    /**
     * 获取处理阶段
     * @return 处理阶段
     */
    public String getProcessingStage() {
        return processingStage;
    }
    
    /**
     * 设置原始SQL
     * @param sql 原始SQL语句
     * @return 当前异常实例
     */
    public SqlProcessingException setOriginalSql(String sql) {
        this.originalSql = sql;
        addContext("originalSql", sql);
        return this;
    }
    
    /**
     * 获取原始SQL
     * @return 原始SQL语句
     */
    public String getOriginalSql() {
        return originalSql;
    }
}