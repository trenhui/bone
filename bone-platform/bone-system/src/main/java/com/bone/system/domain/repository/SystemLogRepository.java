package com.bone.system.domain.repository;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.Repository;
import com.bone.metadata.sdk.query.criteria.Criteria;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.domain.model.log.SystemLog;
import com.bone.system.domain.model.log.valueobject.LogLevel;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统日志仓储端口：写侧 + 本聚合读（ADR-0030）。
 *
 * <p><b>日志为何不拆 QueryPort</b>：日志是单聚合流水，所有读都是「本表过滤 + 时间倒序」，没有跨聚合投影或统计 Join——按 ADR-0030
 * 落在写仓储即可，读引进外壳只会多一层透传（E-3.2 Ceremonial）。只有出现「日志 × 服务拓扑」这类跨聚合组合读时才升级到 {@code
 * application/query/port}。
 */
public interface SystemLogRepository extends Repository<SystemLog, Long> {

  /**
   * 日志分页：内容模糊 + 级别 / 服务名精确过滤，按 {@code createdAt} 倒序。
   *
   * @param keyword 为空或空白时不加内容过滤
   */
  default PageResult<SystemLog> pageByCondition(
      String keyword, LogLevel level, String service, int pageNum, int pageSize) {
    var query = QueryBuilder.from(SystemLog.class);
    appendFilters(query, keyword, level, service);
    return query.orderByDesc(SystemLog::getCreatedAt).page(pageNum, pageSize);
  }

  /**
   * 按条件导出日志（不分页，由调用方限制行数）。
   *
   * <p><b>为什么是内存截断而不是下推 limit</b>：SDK 的 {@code list()} 没有行数下推能力，这里只能先取回再截断， 因此入参 {@code limit}
   * 是<b>内存保护</b>而非查询条件——超大范围导出应改走外置 SQL 读侧仓储（E-4.4）。
   */
  default List<SystemLog> searchForExport(
      String keyword,
      LogLevel level,
      String service,
      LocalDateTime from,
      LocalDateTime to,
      int limit) {
    var query = QueryBuilder.from(SystemLog.class);
    appendFilters(query, keyword, level, service);
    if (from != null) {
      query.and(SystemLog::getCreatedAt).gte(from);
    }
    if (to != null) {
      query.and(SystemLog::getCreatedAt).lte(to);
    }
    return query.orderByDesc(SystemLog::getCreatedAt).list().stream().limit(limit).toList();
  }

  /** 本租户可见的日志总数（Criteria 自带租户与软删过滤，计数不需要 OR）。 */
  default long countAll() {
    return countByCriteria(Criteria.<SystemLog>create().entityClass(SystemLog.class));
  }

  /**
   * 指定级别的本租户日志总数。
   *
   * <p>与 {@code FluentQuery} 相比，Criteria 走的是「单条件等值」，是它的主场（不要拿它拼 OR，见 {@link
   * com.bone.system.domain.repository.SystemConfigRepository} 的缺陷说明）。
   */
  default long countByLevel(LogLevel level) {
    return countByCriteria(
        Criteria.<SystemLog>create().entityClass(SystemLog.class).eq(SystemLog::getLevel, level));
  }

  /**
   * 物理删除早于 {@code cutoff} 的日志（保留期清理，{@code sys_log} 无 {@code deleted} 列故为物理删）。
   *
   * <p><b>为什么放仓储 default 而不是 Jdbc 端口</b>：单表范围删除是本聚合写侧能力，SDK {@code deleteByCriteria} SQL 下推已足够；显式
   * {@code disableTenantFilter()}——保留期是平台级策略， 跨租户生效。返回删除行数供清理 Job 记录审计日志。
   */
  default int purgeOlderThanAllTenants(LocalDateTime cutoff) {
    return deleteByCriteria(
        Criteria.<SystemLog>create()
            .entityClass(SystemLog.class)
            .lt(SystemLog::getCreatedAt, cutoff)
            .disableTenantFilter());
  }

  private static void appendFilters(
      FluentQuery<SystemLog> query, String keyword, LogLevel level, String service) {
    if (keyword != null && !keyword.isBlank()) {
      query.where(SystemLog::getContent).contains(keyword);
    }
    if (level != null) {
      query.and(SystemLog::getLevel).eq(level);
    }
    if (service != null && !service.isBlank()) {
      query.and(SystemLog::getService).eq(service);
    }
  }
}
