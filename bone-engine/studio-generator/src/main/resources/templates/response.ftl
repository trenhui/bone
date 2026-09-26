package ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.dto.response;

import ${utils.getPackagePath(basePackage, moduleName)}.domain.model.${utils.toPackageSegment(table.customEntityName)}.${table.customEntityName};
<#assign needBigDecimal = false><#assign needLocalDate = false><#list columns as c><#if c.javaType == 'BigDecimal'><#assign needBigDecimal = true></#if><#if c.javaType == 'LocalDate'><#assign needLocalDate = true></#if></#list><#if needBigDecimal>import java.math.BigDecimal;
</#if><#if needLocalDate>import java.time.LocalDate;
</#if>import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ${table.tableComment!'实体'}响应对象。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。HC-003：Controller 不裸返领域对象。
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ${table.customEntityName}Response {

  private Long id;
<#list columns as column>
  <#if column.originalColumnName != 'id' && column.originalColumnName != 'created_at' && column.originalColumnName != 'updated_at'>
  private ${column.javaType} ${utils.toFieldName(column.originalColumnName)};
  </#if>
</#list>
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static ${table.customEntityName}Response from(${table.customEntityName} entity) {
    return ${table.customEntityName}Response.builder()
        .id(entity.getId())
<#list columns as column>
  <#if column.originalColumnName != 'id' && column.originalColumnName != 'created_at' && column.originalColumnName != 'updated_at'>
        .${utils.toFieldName(column.originalColumnName)}(entity.get${utils.toCamelCase(column.originalColumnName)}())
  </#if>
</#list>
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
