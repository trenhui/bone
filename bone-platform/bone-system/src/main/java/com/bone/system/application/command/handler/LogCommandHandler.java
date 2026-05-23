package com.bone.system.application.command.handler;

import com.bone.core.capability.Capability;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.system.application.command.cmd.CreateLogCommand;
import com.bone.system.domain.model.log.SystemLog;
import com.bone.system.domain.model.log.vo.LogLevel;
import com.bone.system.domain.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Capability(
    name = "CreateSystemLog",
    description = "创建系统日志",
    inputSchema = "{\"logLevel\": \"string\", \"serviceName\": \"string\", \"content\": \"string\", \"traceId\": \"string\"}",
    outputSchema = "{\"logId\": \"long\"}",
    idempotent = false,
    cost = 1,
    retryable = true,
    timeout = 10
)
@Component
@RequiredArgsConstructor
public class LogCommandHandler {
    private final SystemLogRepository systemLogRepository;

    @Transactional
    public Long handle(CreateLogCommand cmd) {
        Long logId = DistributedIdGenerator.generateLongId();
        SystemLog log = SystemLog.create(
                logId,
                LogLevel.fromString(cmd.getLogLevel()),
                cmd.getServiceName(),
                cmd.getContent(),
                cmd.getTraceId()
        );

        systemLogRepository.save(log);
        return log.getId();
    }
}
