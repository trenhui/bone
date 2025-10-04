package com.bone.metadata.sdk.sql.processor;

import java.util.Map;

/**
 * SQL 处理器统一接口
 */
public interface SqlProcessor {
    /**
     * 处理 SQL 模板
     * @param templateId 模板标识（用于缓存）
     * @param sqlTemplate SQL 模板内容
     * @param params 参数映射
     * @return 处理后的 SQL 和参数
     */
    ProcessedSql process(String templateId, String sqlTemplate, Map<String, Object> params);
}