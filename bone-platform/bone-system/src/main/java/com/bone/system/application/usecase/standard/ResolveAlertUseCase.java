package com.bone.system.application.usecase.standard;

import com.bone.system.application.command.cmd.ResolveAlertCmd;
import com.bone.system.application.command.handler.AlertCommandHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "ResolveAlert",
    description = "标准告警事件解决，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class ResolveAlertUseCase implements UseCaseExecutor<ResolveAlertCmd, Void> {

    private final AlertCommandHandler alertCommandHandler;

    @Override
    public Void execute(ResolveAlertCmd cmd) {
        alertCommandHandler.handle(cmd);
        return null;
    }
}
