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
 * 更新${table.tableComment!'实体'}请求体。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。不含 id：聚合 id 来自路径参数 {@code /{id}}，
 * 请求体只承载业务字段，故 id 不在此处出现。
 */
@Data
public class Update${table.customEntityName}Req {

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
