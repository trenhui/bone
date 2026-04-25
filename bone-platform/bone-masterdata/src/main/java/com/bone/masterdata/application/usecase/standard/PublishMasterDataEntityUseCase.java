package com.bone.masterdata.application.usecase.standard;

import com.bone.masterdata.application.command.handler.PublishMasterDataEntityHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "PublishMasterDataEntity",
    description = "标准主数据实体发布，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class PublishMasterDataEntityUseCase implements UseCaseExecutor<Long, Void> {

    private final PublishMasterDataEntityHandler publishMasterDataEntityHandler;

    @Override
    public Void execute(Long id) {
        publishMasterDataEntityHandler.handle(id);
        return null;
    }
}
