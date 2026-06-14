package com.bone.metadata.sdk.sql.template.parser;

import com.bone.metadata.sdk.domain.enums.SqlTemplateType;
import com.bone.metadata.sdk.domain.exception.TemplateParseException;
import com.bone.metadata.sdk.sql.template.SqlTemplate;
import com.bone.metadata.sdk.sql.template.TemplateDescriptor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * YAML 模板解析器，支持复杂的 SQL 模板配置 示例 YAML 结构： sql: | SELECT * FROM users WHERE 1=1 <if test="name !=
 * null">AND name = :name</if> metadata: description: "用户查询模板" version: "1.0"
 */
@Slf4j
public class YamlTemplateParser implements TemplateContentParser {

  private static final ObjectMapper YAML_MAPPER;

  static {
    YAML_MAPPER = new ObjectMapper(new YAMLFactory());
    // 配置忽略未知属性
    YAML_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    // 可选：配置其他安全选项
    YAML_MAPPER.configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true);
    YAML_MAPPER.configure(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY, true);
  }

  @Override
  public boolean supports(SqlTemplateType format) {
    return format == SqlTemplateType.YAML_SQL;
  }

  @Override
  public SqlTemplate parse(String content, TemplateDescriptor descriptor)
      throws TemplateParseException {
    try {
      log.debug("开始解析YAML模板: {}", descriptor.getTemplateId());

      // 直接反序列化，忽略未知字段
      SqlTemplate template = YAML_MAPPER.readValue(content, SqlTemplate.class);

      // 设置必要的属性
      template.setId(descriptor.getTemplateId());
      template.setSource(descriptor.getSourceUri());
      template.setSqlTemplateType(SqlTemplateType.YAML_SQL);

      // 验证模板
      template.validate();

      log.debug(
          "成功解析YAML模板: {}, SQL长度: {}",
          descriptor.getTemplateId(),
          template.getSql() != null ? template.getSql().length() : 0);
      return template;

    } catch (Exception e) {
      throw new TemplateParseException("解析YAML模板失败: " + descriptor.getTemplateId(), e);
    }
  }
}
