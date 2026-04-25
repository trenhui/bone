package com.bone.masterdata.application.usecase.standard;

import com.bone.masterdata.application.command.cmd.CreateMasterDataEntityCmd;
import com.bone.masterdata.application.command.handler.CreateMasterDataEntityHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "CreateMasterDataEntity",
    description = "标准主数据实体创建，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class CreateMasterDataEntityUseCase implements UseCaseExecutor<CreateMasterDataEntityCmd, Long> {

    private final CreateMasterDataEntityHandler createMasterDataEntityHandler;

    @Override
    public Long execute(CreateMasterDataEntityCmd cmd) {
        return createMasterDataEntityHandler.handle(cmd);
    }
}
