package com.bone.system.application.usecase.standard;

import com.bone.system.application.query.qry.LogPageQry;
import com.bone.system.application.query.handler.LogQueryHandler;
import com.bone.system.application.query.dto.LogDTO;
import com.bone.system.common.result.PageResult;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "LogPageQuery",
    description = "标准日志分页查询，支持 AI 编排与多租户扩展",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class LogPageQueryUseCase implements UseCaseExecutor<LogPageQry, PageResult<LogDTO>> {

    private final LogQueryHandler logQueryHandler;

    @Override
    public PageResult<LogDTO> execute(LogPageQry qry) {
        return logQueryHandler.page(qry);
    }
}
