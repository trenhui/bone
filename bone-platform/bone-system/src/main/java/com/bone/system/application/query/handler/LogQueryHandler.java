package com.bone.system.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.application.query.dto.LogDTO;
import com.bone.system.application.query.qry.LogExportQuery;
import com.bone.system.application.query.qry.LogPageQuery;
import com.bone.system.domain.model.log.SystemLog;
import com.bone.system.domain.model.log.vo.LogLevel;
import com.bone.system.domain.repository.SystemLogRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LogQueryHandler {
  /** 导出行数兜底上限：{@link LogExportQuery#getLimit()} 未设置（或为负）时生效。 */
  private static final int DEFAULT_EXPORT_LIMIT = 10000;

  private final SystemLogRepository systemLogRepository;

  @Transactional(readOnly = true)
  public LogDTO getById(Long id) {
    SystemLog systemLog = systemLogRepository.findById(id);
    return systemLog != null ? toDTO(systemLog) : null;
  }

  @Transactional(readOnly = true)
  public PageResult<LogDTO> page(LogPageQuery qry) {
    FluentQuery<SystemLog> query = QueryBuilder.from(SystemLog.class);

    if (qry.getKeyword() != null && !qry.getKeyword().isBlank()) {
      query.where(SystemLog::getContent).like(qry.getKeyword());
    }

    if (qry.getLogLevel() != null && !qry.getLogLevel().isBlank()) {
      query.where(SystemLog::getLevel).eq(LogLevel.valueOf(qry.getLogLevel()));
    }

    if (qry.getServiceName() != null && !qry.getServiceName().isBlank()) {
      query.where(SystemLog::getService).eq(qry.getServiceName());
    }

    com.bone.core.model.PageResult<SystemLog> result =
        query.orderByDesc(SystemLog::getCreatedAt).page(qry.getPageNum(), qry.getPageSize());

    List<LogDTO> dtoList =
        result.getRecords().stream().map(this::toDTO).collect(Collectors.toList());

    return PageResult.of(dtoList, result.getTotal(), result.getPage(), result.getSize());
  }

  /**
   * 按条件导出日志（MVP-09）。
   *
   * <p><b>为什么在应用层截断而不是下推 limit</b>：SDK 的 {@code list()} 没有行数下推能力，这里只能先取回再截断。 因此 {@link
   * LogExportQuery#getLimit()} 是<b>内存保护</b>而非查询条件——超大范围的导出应改走外置 SQL 读侧仓储 （E-4.4），不要靠调大这个值解决。
   */
  @Transactional(readOnly = true)
  public List<LogDTO> listForExport(LogExportQuery qry) {
    FluentQuery<SystemLog> query = QueryBuilder.from(SystemLog.class);

    if (qry.getKeyword() != null && !qry.getKeyword().isBlank()) {
      query.where(SystemLog::getContent).like(qry.getKeyword());
    }
    if (qry.getLogLevel() != null && !qry.getLogLevel().isBlank()) {
      query.where(SystemLog::getLevel).eq(LogLevel.valueOf(qry.getLogLevel()));
    }
    if (qry.getServiceName() != null && !qry.getServiceName().isBlank()) {
      query.where(SystemLog::getService).eq(qry.getServiceName());
    }
    if (qry.getStartTime() != null) {
      query.where(SystemLog::getCreatedAt).gte(qry.getStartTime());
    }
    if (qry.getEndTime() != null) {
      query.where(SystemLog::getCreatedAt).lte(qry.getEndTime());
    }

    int limit = qry.getLimit() > 0 ? qry.getLimit() : DEFAULT_EXPORT_LIMIT;
    return query.orderByDesc(SystemLog::getCreatedAt).list().stream()
        .limit(limit)
        .map(this::toDTO)
        .collect(Collectors.toList());
  }

  /**
   * 日志分析概览（MVP-09「日志查询」的衍生读接口）。
   *
   * <p>只做轻量聚合：总量 + 各级别计数。SDK 的 {@code FluentQuery} 无 GROUP BY，按级别逐一 {@code count()} 是 O(级别数)
   * 次查询，级别固定 5 种，成本可控；服务维度分布等更重的统计留给 OLAP，不在这里凑。
   */
  @Transactional(readOnly = true)
  public Map<String, Object> analyze() {
    Map<String, Object> result = new LinkedHashMap<>();
    long total = QueryBuilder.from(SystemLog.class).count();
    Map<String, Long> byLevel = new LinkedHashMap<>();
    for (LogLevel lv : LogLevel.values()) {
      long c = QueryBuilder.from(SystemLog.class).where(SystemLog::getLevel).eq(lv).count();
      byLevel.put(lv.name(), c);
    }
    result.put("total", total);
    result.put("byLevel", byLevel);
    return result;
  }

  private LogDTO toDTO(SystemLog log) {
    return LogDTO.builder()
        .id(log.getId())
        .logLevel(log.getLevel().name())
        .serviceName(log.getService())
        .content(log.getContent())
        .traceId(log.getTraceId())
        .createdAt(log.getCreatedAt())
        .build();
  }
}
