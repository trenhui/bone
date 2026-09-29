package ${utils.getPackagePath(basePackage, moduleName)}.domain.repository;

import ${utils.getPackagePath(basePackage, moduleName)}.domain.model.${utils.toPackageSegment(table.customEntityName)}.${table.customEntityName};
import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;

/**
 * ${table.tableComment!'实体'}仓储。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。接口定义留在 {@code domain.repository}，<b>不生成实现类</b>——
 * 实现由 {@code @EnableSqlRepositories} 代理（blueprint {@code domain/repository/package-info.java} 同口径），
 * 手写 {@code XxxRepositoryImpl} 会踩持久化唯一约束 HC-001 / HC-006。
 */
public interface ${table.customEntityName}Repository extends Repository<${table.customEntityName}, Long> {

  /**
   * 分页查询（按创建时间倒序）。
   *
   * <p><b>不手写 tenant_id 条件</b>：聚合继承 {@code TenantAggregateRoot}，实体含 {@code tenant_id} 列，SDK 按 ADR-0029
   * 自动在 WHERE 注入 {@code m.tenant_id = :_sdk_tenant_id}。手写会被 SDK 忽略并打 WARN（调用方租户不可信）；
   * 跨租户平台级扫描才需显式 {@code disableTenantFilter()} + {@code platform:*} 授权与审计。
   */
  default PageResult<${table.customEntityName}> findPage(int pageNum, int pageSize) {
    return pageByCriteria(
        Criteria.<${table.customEntityName}>create()
            .orderByDesc(${table.customEntityName}::getCreatedAt)
            .page(pageNum, pageSize));
  }
}
