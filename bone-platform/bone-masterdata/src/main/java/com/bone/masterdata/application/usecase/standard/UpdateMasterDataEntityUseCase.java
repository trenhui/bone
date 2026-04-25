package com.bone.masterdata.application.usecase.standard;

import com.bone.masterdata.application.command.cmd.UpdateMasterDataEntityCmd;
import com.bone.masterdata.application.command.handler.UpdateMasterDataEntityHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "UpdateMasterDataEntity",
    description = "标准主数据实体更新，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class UpdateMasterDataEntityUseCase implements UseCaseExecutor<UpdateMasterDataEntityCmd, Void> {

    private final UpdateMasterDataEntityHandler updateMasterDataEntityHandler;

    @Override
    public Void execute(UpdateMasterDataEntityCmd cmd) {
        updateMasterDataEntityHandler.handle(cmd);
        return null;
    }
}
