package com.bone.iam.application.usecase.standard;

import com.bone.iam.application.command.cmd.DisableAccountCmd;
import com.bone.iam.application.command.handler.DisableAccountHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "DisableAccount",
    description = "标准账户禁用，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class DisableAccountUseCase implements UseCaseExecutor<DisableAccountCmd, Void> {

    private final DisableAccountHandler disableAccountHandler;

    @Override
    public Void execute(DisableAccountCmd cmd) {
        disableAccountHandler.handle(cmd);
        return null;
    }
}
