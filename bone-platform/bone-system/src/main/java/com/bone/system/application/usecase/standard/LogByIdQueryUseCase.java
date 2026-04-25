package com.bone.system.application.usecase.standard;

import com.bone.system.application.query.dto.LogDTO;
import com.bone.system.application.query.handler.LogQueryHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "LogByIdQuery",
    description = "标准日志根据ID查询，支持 AI 编排与多租户扩展",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class LogByIdQueryUseCase implements UseCaseExecutor<Long, LogDTO> {

    private final LogQueryHandler logQueryHandler;

    @Override
    public LogDTO execute(Long id) {
        return logQueryHandler.getById(id);
    }
}
