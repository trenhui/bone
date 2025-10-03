package com.bone.metadata.sdk.sql.template.parser;


import com.bone.metadata.sdk.domain.enums.SqlTemplateType;
import com.bone.metadata.sdk.sql.template.SqlTemplate;
import com.bone.metadata.sdk.sql.template.TemplateDescriptor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MyBatisTemplateParser implements TemplateContentParser {

    @Override
    public boolean supports(SqlTemplateType format) {
        return format == SqlTemplateType.MYBATIS;
    }

    @Override
    public SqlTemplate parse(String content, TemplateDescriptor descriptor) {
        SqlTemplate template = new SqlTemplate();
        template.setId(descriptor.getTemplateId());
        template.setSql(content.trim());
        template.setSqlTemplateType(SqlTemplateType.MYBATIS);
        template.setSource(descriptor.getSourceUri());
        template.setSqlType(detectSqlType(content));
        return template;
    }
}