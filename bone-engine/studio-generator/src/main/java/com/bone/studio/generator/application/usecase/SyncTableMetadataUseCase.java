package com.bone.studio.generator.application.usecase;

import com.bone.core.usecase.UseCase;
import com.bone.studio.generator.application.command.cmd.SyncTableMetadataCmd;
import com.bone.studio.generator.application.command.handler.SyncTableMetadataHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(name = "SyncTableMetadata", description = "同步表元数据", transactional = false)
@Service
@RequiredArgsConstructor
public class SyncTableMetadataUseCase implements UseCaseExecutor<SyncTableMetadataCmd, Void> {

    private final SyncTableMetadataHandler syncHandler;

    @Override
    public Void execute(SyncTableMetadataCmd cmd) {
        syncHandler.handle(cmd);
        return null;
    }
}
