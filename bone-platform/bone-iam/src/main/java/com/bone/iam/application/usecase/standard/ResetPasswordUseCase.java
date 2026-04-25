package com.bone.iam.application.usecase.standard;

import com.bone.iam.application.command.cmd.ResetPasswordCmd;
import com.bone.iam.application.command.handler.ResetPasswordHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "ResetPassword",
    description = "标准密码重置，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class ResetPasswordUseCase implements UseCaseExecutor<ResetPasswordCmd, Void> {

    private final ResetPasswordHandler resetPasswordHandler;

    @Override
    public Void execute(ResetPasswordCmd cmd) {
        resetPasswordHandler.handle(cmd);
        return null;
    }
}
