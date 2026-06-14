package com.bone.system.application.query.handler;

import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.query.dsl.FluentQuery;
import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.system.application.query.dto.LogDTO;
import com.bone.system.application.query.qry.LogPageQuery;
import com.bone.system.domain.model.log.SystemLog;
import com.bone.system.domain.model.log.vo.LogLevel;
import com.bone.system.domain.repository.SystemLogRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LogQueryHandler {
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
