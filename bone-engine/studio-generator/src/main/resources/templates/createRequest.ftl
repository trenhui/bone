<#assign needNotBlank = false><#assign needNotNull = false><#list businessColumns as column><#if !column.nullable><#if column.javaType == 'String'><#assign needNotBlank = true><#else><#assign needNotNull = true></#if></#if></#list>
package ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.dto.request;

<#list businessTypeImports as javaTypeImport>
import ${javaTypeImport};
</#list>
<#if needNotBlank>
import jakarta.validation.constraints.NotBlank;
</#if>
<#if needNotNull>
import jakarta.validation.constraints.NotNull;
</#if>
import lombok.Data;

/**
 * 创建${table.tableComment!'实体'}请求体。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。约束直接取自列的 {@code NOT NULL} 标记：结构校验进 HTTP 契约，
 * 业务不变量仍在聚合 {@code create()} 里由 {@code DomainException} 兜住——两者不可互相替代。
 */
@Data
public class Create${table.customEntityName}Req {

<#list businessColumns as column>
  /** ${column.comment} */
<#if !column.nullable>
<#if column.javaType == 'String'>
  @NotBlank(message = "${column.comment}不能为空")
<#else>
  @NotNull(message = "${column.comment}不能为空")
</#if>
</#if>
  private ${column.javaType} ${column.fieldName};

</#list>
}
