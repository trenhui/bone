package ${utils.getPackagePath(basePackage, moduleName)}.domain.model.${utils.toPackageSegment(table.customEntityName)};

import com.bone.core.annotation.Id;
import com.bone.core.domain.AggregateRoot;
import com.bone.core.domain.id.GeneratedValue;
import com.bone.core.domain.id.GenerationStrategy;
import com.bone.metadata.sdk.domain.annotation.Column;
import com.bone.metadata.sdk.domain.annotation.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ${table.tableComment!'实体'}。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table("${table.originalTableName}")
public class ${table.customEntityName} extends AggregateRoot<Long> {

  @Id
  @GeneratedValue(strategy = GenerationStrategy.DISTRIBUTED_ID)
  private Long id;
<#list columns as column>
  <#if column.originalColumnName != 'id' && column.originalColumnName != 'created_at' && column.originalColumnName != 'updated_at'>
  /** ${column.columnComment!''} */
  @Column(name = "${column.originalColumnName}")
  private ${column.javaType} ${utils.toFieldName(column.originalColumnName)};
  </#if>
</#list>

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
