package com.bone.system.application;

import com.bone.core.capability.Capability;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.system.application.command.CreateLogCommand;
import com.bone.system.application.query.dto.LogDto;
import com.bone.system.application.query.qry.LogExportQuery;
import com.bone.system.application.query.qry.LogPageQuery;
import com.bone.system.common.SystemErrorCodes;
import com.bone.system.common.SystemErrors;
import com.bone.system.domain.model.log.SystemLog;
import com.bone.system.domain.model.log.valueobject.LogLevel;
import com.bone.system.domain.repository.SystemLogRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统日志用例入口（写 + 读 + 导出同一入口，ADR-0028）。
 *
 * <p><b>日志为何不需要 QueryPort</b>：所有读都是单聚合、单表、本租户的过滤与倒序，读模型与聚合结构一致 （E-4.2 简单读直查）。将来若出现「日志 ×
 * 服务拓扑」这类跨上下文组合读，再按 E-4.2 升级 {@code application/query/port}，不要提前预生成端口（E-3.11 最小生成）。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemLogApplicationService {

  /** 导出行数兜底上限：入参未设置（或为负）时生效。 */
  private static final int DEFAULT_EXPORT_LIMIT = 10000;

  private final SystemLogRepository systemLogRepository;
  private final DomainEventPublisher domainEventPublisher;

  @Capability(
      name = "CreateSystemLog",
      description = "创建系统日志",
      inputSchema =
          "{\"logLevel\": \"string\", \"serviceName\": \"string\", \"content\": \"string\","
              + " \"traceId\": \"string\"}",
      outputSchema = "{\"logId\": \"long\"}",
      idempotent = false,
      cost = 1,
      retryable = true,
      timeout = 10)
  @Transactional
  public Long create(CreateLogCommand command) {
    SystemLog log =
        SystemLog.create(
            DistributedIdGenerator.generateLongId(),
            parseLogLevel(command.getLogLevel()),
            command.getServiceName(),
            command.getContent(),
            command.getTraceId());
    systemLogRepository.save(log);
    domainEventPublisher.publishFrom(log);
    return log.getId();
  }

  public Optional<LogDto> getById(Long id) {
    return Optional.ofNullable(systemLogRepository.findById(id)).map(LogDto::from);
  }

  public PageResult<LogDto> page(LogPageQuery query) {
    PageResult<SystemLog> page =
        systemLogRepository.pageByCondition(
            query.getKeyword(),
            parseLogLevelOrNull(query.getLogLevel()),
            query.getServiceName(),
            query.getPageNum(),
            query.getPageSize());
    return PageResult.of(
        page.getRecords().stream().map(LogDto::from).toList(),
        page.getTotal(),
        page.getPage(),
        page.getSize());
  }

  /**
   * 按条件取回待导出的日志（调用方负责 CSV 序列化）。
   *
   * <p>行数是<b>内存保护</b>而不是查询下推（SDK 的 {@code list()} 不支持 limit 下推，见 {@link
   * SystemLogRepository#searchForExport}）：{@code limit <= 0} 时按 {@link #DEFAULT_EXPORT_LIMIT}
   * 兜底，超大范围导出应改走外置 SQL 读侧仓储（E-4.4），不要靠调大这个值解决。
   */
  public List<LogDto> listForExport(LogExportQuery query) {
    LogLevel level = parseLogLevelOrNull(query.getLogLevel());
    int limit = query.getLimit() > 0 ? query.getLimit() : DEFAULT_EXPORT_LIMIT;
    return systemLogRepository
        .searchForExport(
            query.getKeyword(),
            level,
            query.getServiceName(),
            query.getStartTime(),
            query.getEndTime(),
            limit)
        .stream()
        .map(LogDto::from)
        .toList();
  }

  /**
   * 日志概览：总量 + 各级别计数（MVP-09 日志查询的衍生读接口）。
   *
   * <p><b>为什么是逐级别 count 而不是一次 GROUP BY</b>：SDK 的读侧通道没有 GROUP BY，按级别逐一 {@code count()} 是 O(级别数)
   * 次查询；{@code LogLevel} 固定 5 种，成本可控且随级别数线性。 服务维度分布等更重的统计应交给 OLAP，不在这里凑。
   *
   * @return {@code {total: long, byLevel: {LEVEL: count}}}
   */
  public Map<String, Object> analyze() {
    Map<String, Long> byLevel = new LinkedHashMap<>();
    for (LogLevel level : LogLevel.values()) {
      byLevel.put(level.name(), systemLogRepository.countByLevel(level));
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("total", systemLogRepository.countAll());
    result.put("byLevel", byLevel);
    return result;
  }

  private static LogLevel parseLogLevel(String value) {
    try {
      return LogLevel.fromString(value);
    } catch (RuntimeException ex) {
      throw SystemErrors.of(SystemErrorCodes.LOG_LEVEL_INVALID, value);
    }
  }

  /** 过滤条件可缺省：这里 null 表示「不按级别过滤」，空串是合法缺省值，不要把它翻译成 400。 */
  private static LogLevel parseLogLevelOrNull(String value) {
    return value == null || value.isBlank() ? null : parseLogLevel(value);
  }
}
