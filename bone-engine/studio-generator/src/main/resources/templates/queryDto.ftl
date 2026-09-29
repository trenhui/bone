package ${utils.getPackagePath(basePackage, moduleName)}.application.query.dto;

import ${utils.getPackagePath(basePackage, moduleName)}.domain.model.${utils.toPackageSegment(table.customEntityName)}.${table.customEntityName};
<#list businessTypeImports as javaTypeImport>
import ${javaTypeImport};
</#list>
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * ${table.tableComment!'实体'}应用层读模型。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。
 *
 * <p><b>为何必须有这一层</b>：此前应用服务直接返回 {@code adapter.web.dto.response.*Response}，形成 {@code application →
 * adapter} 的反向依赖，违反 {@code adapter → application → domain} 的分层方向。应用层只出 {@code Dto}，
 * 由 {@code adapter} 层 Assembler 翻译成对外契约 {@code *Resp}（blueprint {@code OrderDto → OrderSummaryResp} 同口径）。
 */
@Getter
@Builder
public class ${table.customEntityName}Dto {

  private Long id;
<#list businessColumns as column>
  /** ${column.comment} */
  private ${column.javaType} ${column.fieldName};

</#list>
  private Instant createdAt;
  private Instant updatedAt;

  /**
   * 由聚合构造读模型。
   *
   * <p>出口时间字段一律 {@code Instant}（序列化带 {@code Z}），与 blueprint {@code OrderSummaryResp} 一致，
   * 避免 {@code LocalDateTime} 在不同时区部署下解析漂移。
   */
  public static ${table.customEntityName}Dto from(${table.customEntityName} entity) {
    return ${table.customEntityName}Dto.builder()
        .id(entity.getId())
<#list businessColumns as column>
        .${column.fieldName}(entity.${column.getter}())
</#list>
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
