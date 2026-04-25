package com.bone.integration.application.usecase.standard;

import com.bone.integration.application.query.qry.ConnectorPageQry;
import com.bone.integration.application.query.handler.ConnectorPageQueryHandler;
import com.bone.integration.application.query.dto.ConnectorDTO;
import com.bone.core.model.PageResult;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "ConnectorPageQuery",
    description = "标准连接器分页查询，支持 AI 编排与多租户扩展",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class ConnectorPageQueryUseCase implements UseCaseExecutor<ConnectorPageQry, PageResult<ConnectorDTO>> {

    private final ConnectorPageQueryHandler connectorPageQueryHandler;

    @Override
    public PageResult<ConnectorDTO> execute(ConnectorPageQry qry) {
        return connectorPageQueryHandler.handle(qry);
    }
}
