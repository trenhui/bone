package com.bone.system.application.command.handler;

import com.bone.system.application.command.cmd.CreateLogCmd;
import com.bone.system.domain.model.log.SystemLog;
import com.bone.system.domain.model.log.vo.LogLevel;
import com.bone.system.domain.repository.SystemLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class LogCommandHandler {
    private final SystemLogRepository systemLogRepository;

    @Transactional
    public Long handle(CreateLogCmd cmd) {
        SystemLog log = SystemLog.create(
                LogLevel.fromString(cmd.getLogLevel()),
                cmd.getServiceName(),
                cmd.getContent(),
                cmd.getTraceId()
        );

        systemLogRepository.save(log);
        return log.getId();
    }
}
