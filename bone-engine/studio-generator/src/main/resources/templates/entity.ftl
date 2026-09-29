package ${utils.getPackagePath(basePackage, moduleName)}.domain.model.${utils.toPackageSegment(table.customEntityName)};

import com.bone.core.domain.TenantAggregateRoot;
import com.bone.core.exception.DomainException;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.metadata.sdk.domain.annotation.Table;
import com.bone.metadata.sdk.domain.annotation.Version;
<#list businessTypeImports as javaTypeImport>
import ${javaTypeImport};
</#list>
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * ${table.tableComment!'实体'}聚合根。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。
 *
 * <p><b>为何继承 TenantAggregateRoot 而不是 AggregateRoot</b>：业务实体必须带 {@code tenantId} 并落到 {@code
 * tenant_id} 列（HC-008 租户与审计）；SDK 据此识别为租户表，在 SELECT / UPDATE / DELETE 自动注入租户过滤（ADR-0029）。
 *
 * <p><b>为何没有 @Column</b>：列名由 {@code @Table} + 字段名驼峰转下划线推导，blueprint 全模块零 {@code @Column}；
 * 只在列名不符合推导规则时才显式标注。
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Table("${table.originalTableName}")
public class ${table.customEntityName} extends TenantAggregateRoot<Long> {

<#list businessColumns as column>
  /** ${column.comment} */
  private ${column.javaType} ${column.fieldName};

</#list>
  private Instant createdAt;
  private Instant updatedAt;

  /**
   * 乐观锁版本号（ADR-0031 D2）：由 {@code bone-metadata-sdk} 原子维护——写路径改写 {@code SET version = version + 1}、
   * {@code WHERE version = :old}，并发写 0 行由 SDK 抛 {@code OptimisticLockingFailureException}，在应用层翻译为 409。
   * 领域层不再手写 {@code incrementVersion()}（已随 D2 退役），这里只置初值 0 与库一致。
   */
  @Version
  private Long version;

  /**
   * 创建${table.tableComment!'实体'}：不变量在构造期校验完毕，身份由 {@code DistributedIdGenerator} 预分配（ADR-0019）。
   *
   * <p><b>为何不带 tenantId 入参</b>：写路径由 SDK 从 {@code TenantContext} 补正 {@code tenant_id}（ADR-0029 写侧补正），
   * 应用层无需直读 {@code TenantContext}（E-2 要求业务层不直接取租户上下文）。
   */
  public static ${table.customEntityName} create(<#list businessColumns as column>${column.javaType} ${column.fieldName}<#sep>, </#sep></#list>) {
<#list businessColumns as column>
<#if !column.nullable>
    if (${column.fieldName} == null) {
      throw new DomainException("${column.comment}不能为空");
    }
</#if>
</#list>
    ${table.customEntityName} entity = new ${table.customEntityName}();
    entity.setId(DistributedIdGenerator.generateLongId());
<#list businessColumns as column>
    entity.${column.fieldName} = ${column.fieldName};
</#list>
    entity.version = 0L;
    Instant now = Instant.now();
    entity.createdAt = now;
    entity.updatedAt = now;
    return entity;
  }

  /**
   * 更新${table.tableComment!'实体'}业务字段。
   *
   * <p><b>不发 DomainEvent</b>：内部状态迁移，无跨聚合协作需求（E-5.4 豁免一类）。若后续需要发布领域事件，
   * 在应用服务去掉 {@code @NoDomainEvent} 并配对 {@code DomainEventPublisher#publishFrom}。
   */
  public void applyUpdate(<#list businessColumns as column>${column.javaType} ${column.fieldName}<#sep>, </#sep></#list>) {
<#list businessColumns as column>
<#if !column.nullable>
    if (${column.fieldName} == null) {
      throw new DomainException("${column.comment}不能为空");
    }
</#if>
</#list>
<#list businessColumns as column>
    this.${column.fieldName} = ${column.fieldName};
</#list>
    this.updatedAt = Instant.now();
  }
}
