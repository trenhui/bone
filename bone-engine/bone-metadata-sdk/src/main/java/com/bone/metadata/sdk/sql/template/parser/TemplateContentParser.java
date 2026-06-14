package com.bone.metadata.sdk.sql.template.parser;

import com.bone.metadata.sdk.domain.annotation.SqlType;
import com.bone.metadata.sdk.domain.enums.SqlTemplateType;
import com.bone.metadata.sdk.domain.exception.TemplateParseException;
import com.bone.metadata.sdk.sql.template.SqlTemplate;
import com.bone.metadata.sdk.sql.template.TemplateDescriptor;

public interface TemplateContentParser {
  boolean supports(SqlTemplateType format);

  SqlTemplate parse(String content, TemplateDescriptor descriptor) throws TemplateParseException;

  default SqlType detectSqlType(String sql) {
    String upperSql = sql.toUpperCase().trim();
    if (upperSql.startsWith("SELECT")) return SqlType.SELECT;
    if (upperSql.startsWith("INSERT")) return SqlType.INSERT;
    if (upperSql.startsWith("UPDATE")) return SqlType.UPDATE;
    if (upperSql.startsWith("DELETE")) return SqlType.DELETE;
    if (upperSql.startsWith("WITH")) return SqlType.CTE;
    return SqlType.UNKNOWN;
  }
}
