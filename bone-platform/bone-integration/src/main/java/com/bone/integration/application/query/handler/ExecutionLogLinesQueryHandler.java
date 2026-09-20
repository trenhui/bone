package com.bone.integration.application.query.handler;

import com.bone.core.exception.DomainException;
import com.bone.integration.domain.execution.IntegrationLog;
import com.bone.integration.domain.repository.IntegrationLogRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 查询某次执行（execution）的过程日志行。
 *
 * <p>MVP+ 阶段集成执行尚未落独立日志行表，此处按执行记录的生命周期投影出可读日志行， 供前端执行详情页展示。
 */
@Component
@RequiredArgsConstructor
public class ExecutionLogLinesQueryHandler {

  private final IntegrationLogRepository logRepository;

  @Transactional(readOnly = true)
  public List<Map<String, Object>> handle(Long executionId) {
    IntegrationLog log = logRepository.findById(executionId);
    if (log == null) {
      throw new DomainException("执行记录不存在");
    }

    List<Map<String, Object>> lines = new ArrayList<>();
    if (log.getStartedAt() != null) {
      lines.add(line(log.getStartedAt(), "INFO", "流程开始执行"));
    }
    if (log.getOutputData() != null) {
      lines.add(line(log.getEndedAt(), "INFO", "执行结果: " + log.getOutputData()));
    }
    if (log.getErrorMessage() != null) {
      lines.add(line(log.getEndedAt(), "ERROR", log.getErrorMessage()));
    }
    if (log.getEndedAt() != null) {
      lines.add(line(log.getEndedAt(), "INFO", "执行结束，状态: " + log.getStatus().name()));
    }
    if (lines.isEmpty()) {
      lines.add(line(LocalDateTime.now(), "INFO", "执行尚未开始，状态: " + log.getStatus().name()));
    }
    return lines;
  }

  private Map<String, Object> line(LocalDateTime timestamp, String level, String message) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("timestamp", timestamp);
    m.put("level", level);
    m.put("message", message);
    return m;
  }
}
