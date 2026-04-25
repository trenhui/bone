package com.bone.system.application.usecase.standard;

import com.bone.system.application.query.dto.AlertEventDTO;
import com.bone.system.application.query.handler.AlertQueryHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "AlertEventByIdQuery",
    description = "标准告警事件根据ID查询，支持 AI 编排与多租户扩展",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class AlertEventByIdQueryUseCase implements UseCaseExecutor<Long, AlertEventDTO> {

    private final AlertQueryHandler alertQueryHandler;

    @Override
    public AlertEventDTO execute(Long id) {
        return alertQueryHandler.getEventById(id);
    }
}
