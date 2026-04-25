package com.bone.system.application.usecase.standard;

import com.bone.system.application.command.handler.AlertCommandHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "DeleteAlertRule",
    description = "标准告警规则删除，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class DeleteAlertRuleUseCase implements UseCaseExecutor<Long, Void> {

    private final AlertCommandHandler alertCommandHandler;

    @Override
    public Void execute(Long id) {
        alertCommandHandler.delete(id);
        return null;
    }
}
