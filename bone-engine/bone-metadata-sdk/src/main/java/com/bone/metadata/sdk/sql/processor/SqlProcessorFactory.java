package com.bone.metadata.sdk.sql.processor;

import com.bone.metadata.sdk.domain.enums.SqlTemplateType;
import com.bone.metadata.sdk.support.config.SqlConfigProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * SQL 处理器工厂，根据模板类型提供相应的处理器
 */
public class SqlProcessorFactory {

    private final Map<SqlTemplateType, SqlProcessor> processors = new EnumMap<>(SqlTemplateType.class);
    private SqlProcessor defaultProcessor;

    public SqlProcessorFactory(SqlConfigProperties properties) {
        // 初始化处理器映射 - 移除冗余的PassThroughSqlProcessor，直接使用MyBatisSqlProcessor
        processors.put(SqlTemplateType.SQL, new MyBatisSqlProcessor(properties));
        processors.put(SqlTemplateType.MYBATIS, new MyBatisSqlProcessor(properties));
        processors.put(SqlTemplateType.YAML_SQL, new DynamicSqlProcessor());

        // 设置默认处理器（不再使用 null 键）
        this.defaultProcessor = processors.get(SqlTemplateType.YAML_SQL);
    }

    /**
     * 根据模板类型获取相应的 SQL 处理器
     */
    public SqlProcessor getProcessor(SqlTemplateType type) {
        if (type == null) {
            return defaultProcessor;
        }
        return processors.getOrDefault(type, defaultProcessor);
    }

    /**
     * 设置默认处理器
     */
    public void setDefaultProcessor(SqlTemplateType type) {
        SqlProcessor processor = processors.get(type);
        if (processor != null) {
            this.defaultProcessor = processor;
        }
    }

    /**
     * 直接设置默认处理器实例
     */
    public void setDefaultProcessor(SqlProcessor processor) {
        if (processor != null) {
            this.defaultProcessor = processor;
        }
    }
}