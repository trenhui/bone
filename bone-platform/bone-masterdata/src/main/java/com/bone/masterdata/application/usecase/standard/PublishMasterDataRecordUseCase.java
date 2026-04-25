package com.bone.masterdata.application.usecase.standard;

import com.bone.masterdata.application.command.handler.PublishMasterDataRecordHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "PublishMasterDataRecord",
    description = "标准主数据记录发布，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class PublishMasterDataRecordUseCase implements UseCaseExecutor<Long, Void> {

    private final PublishMasterDataRecordHandler publishMasterDataRecordHandler;

    @Override
    public Void execute(Long id) {
        publishMasterDataRecordHandler.handle(id);
        return null;
    }
}
