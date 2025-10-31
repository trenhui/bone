package com.bone.metadata.sdk.sql.template.parser;

import com.bone.metadata.sdk.domain.enums.SqlTemplateType;
import com.bone.metadata.sdk.domain.exception.TemplateParseException;
import com.bone.metadata.sdk.sql.template.SqlTemplate;
import com.bone.metadata.sdk.sql.template.TemplateDescriptor;
import lombok.extern.slf4j.Slf4j;

/**
 * 精简优化的 SQL 模板解析器
 * 职责：专注于模板解析和元数据提取，安全验证委托给 SqlProcessor
 */
@Slf4j
public class SqlTemplateParser implements TemplateContentParser {

    @Override
    public boolean supports(SqlTemplateType format) {
        return format == SqlTemplateType.SQL||format == SqlTemplateType.MYBATIS;
    }

    @Override
    public SqlTemplate parse(String content, TemplateDescriptor descriptor) throws TemplateParseException {
        SqlTemplate template = new SqlTemplate();
        template.setId(descriptor.getTemplateId());
        template.setSql(content.trim());
        template.setSqlTemplateType(descriptor.getFormat());
        template.setSource(descriptor.getSourceUri());
        template.setSqlType(detectSqlType(content));
        return template;
    }
}