package com.bone.system.application.usecase.standard;

import com.bone.system.application.query.dto.ConfigDTO;
import com.bone.system.application.query.handler.ConfigQueryHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "ConfigByKeyQuery",
    description = "标准系统配置根据键查询，支持 AI 编排与多租户扩展",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class ConfigByKeyQueryUseCase implements UseCaseExecutor<String, ConfigDTO> {

    private final ConfigQueryHandler configQueryHandler;

    @Override
    public ConfigDTO execute(String key) {
        return configQueryHandler.getByKey(key);
    }
}
