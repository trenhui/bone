package ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.dto.response;

<#list businessTypeImports as javaTypeImport>
import ${javaTypeImport};
</#list>
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

/**
 * ${table.tableComment!'实体'}响应契约。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。命名对齐《Bone-DDD》E-13.1 的 {@code *Resp} 后缀
 * （blueprint {@code OrderSummaryResp} 里的注释锚点）；由 Assembler 从应用层 {@code ${table.customEntityName}Dto}
 * 翻译而来，聚合对象不外泄到 HTTP 出口（HC-003）。
 */
@Data
@Builder
public class ${table.customEntityName}Resp {

  private Long id;
<#list businessColumns as column>
  /** ${column.comment} */
  private ${column.javaType} ${column.fieldName};

</#list>
  private Instant createdAt;
  private Instant updatedAt;
}
