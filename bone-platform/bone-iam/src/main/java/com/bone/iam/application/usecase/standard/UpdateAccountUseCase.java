package com.bone.iam.application.usecase.standard;

import com.bone.iam.application.command.cmd.UpdateAccountCmd;
import com.bone.iam.application.command.handler.UpdateAccountHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "UpdateAccount",
    description = "标准账户更新，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class UpdateAccountUseCase implements UseCaseExecutor<UpdateAccountCmd, Void> {

    private final UpdateAccountHandler updateAccountHandler;

    @Override
    public Void execute(UpdateAccountCmd cmd) {
        updateAccountHandler.handle(cmd);
        return null;
    }
}
