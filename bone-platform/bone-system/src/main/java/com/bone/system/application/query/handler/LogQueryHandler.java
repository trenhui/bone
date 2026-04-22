package com.bone.system.application.query.handler;

import com.bone.metadata.sdk.query.QueryBuilder;
import com.bone.system.application.query.dto.LogDTO;
import com.bone.system.application.query.qry.LogPageQry;
import com.bone.system.common.result.PageResult;
import com.bone.system.domain.model.log.SystemLog;
import com.bone.system.domain.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LogQueryHandler {
    private final SystemLogRepository systemLogRepository;

    @Transactional(readOnly = true)
    public LogDTO getById(Long id) {
        return systemLogRepository.findById(id)
                .map(this::toDTO)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public PageResult<LogDTO> page(LogPageQry qry) {
        QueryBuilder<SystemLog> queryBuilder = QueryBuilder.from(SystemLog.class);
        
        if (qry.getKeyword() != null && !qry.getKeyword().isBlank()) {
            queryBuilder = queryBuilder.where("content").like(qry.getKeyword());
        }
        
        if (qry.getLogLevel() != null && !qry.getLogLevel().isBlank()) {
            queryBuilder = queryBuilder.where("logLevel").eq(qry.getLogLevel());
        }
        
        if (qry.getServiceName() != null && !qry.getServiceName().isBlank()) {
            queryBuilder = queryBuilder.where("serviceName").eq(qry.getServiceName());
        }

        return queryBuilder
                .orderBy("createTime", "desc")
                .page(qry.getPageNum(), qry.getPageSize())
                .mapTo(LogDTO.class);
    }

    private LogDTO toDTO(SystemLog log) {
        return LogDTO.builder()
                .id(log.getId())
                .logLevel(log.getLogLevel().name())
                .serviceName(log.getServiceName())
                .content(log.getContent())
                .traceId(log.getTraceId())
                .createTime(log.getCreateTime())
                .build();
    }
}
