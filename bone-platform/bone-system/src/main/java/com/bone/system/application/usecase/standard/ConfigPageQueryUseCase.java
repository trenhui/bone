package com.bone.system.application.usecase.standard;

import com.bone.system.application.query.qry.ConfigPageQry;
import com.bone.system.application.query.handler.ConfigQueryHandler;
import com.bone.system.application.query.dto.ConfigDTO;
import com.bone.system.common.result.PageResult;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "ConfigPageQuery",
    description = "标准系统配置分页查询，支持 AI 编排与多租户扩展",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class ConfigPageQueryUseCase implements UseCaseExecutor<ConfigPageQry, PageResult<ConfigDTO>> {

    private final ConfigQueryHandler configQueryHandler;

    @Override
    public PageResult<ConfigDTO> execute(ConfigPageQry qry) {
        return configQueryHandler.page(qry);
    }
}
