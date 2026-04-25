package com.bone.integration.application.usecase.standard;

import com.bone.integration.application.query.qry.FlowPageQry;
import com.bone.integration.application.query.handler.FlowPageQueryHandler;
import com.bone.integration.application.query.dto.FlowDTO;
import com.bone.core.model.PageResult;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "FlowPageQuery",
    description = "标准集成流程分页查询，支持 AI 编排与多租户扩展",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class FlowPageQueryUseCase implements UseCaseExecutor<FlowPageQry, PageResult<FlowDTO>> {

    private final FlowPageQueryHandler flowPageQueryHandler;

    @Override
    public PageResult<FlowDTO> execute(FlowPageQry qry) {
        return flowPageQueryHandler.handle(qry);
    }
}
