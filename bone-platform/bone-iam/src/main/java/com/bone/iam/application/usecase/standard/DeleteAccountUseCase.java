package com.bone.iam.application.usecase.standard;

import com.bone.core.usecase.UseCase;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.iam.application.command.handler.DeleteAccountHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
        name = "DeleteAccount",
        description = "删除账号",
        transactional = true
)
@Service
@RequiredArgsConstructor
public class DeleteAccountUseCase implements UseCaseExecutor<Long, Void> {
    private final DeleteAccountHandler deleteAccountHandler;

    @Override
    public Void execute(Long id) {
        deleteAccountHandler.handle(id);
        return null;
    }
}

