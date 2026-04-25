package com.bone.system.application.usecase.standard;

import com.bone.system.application.query.handler.AlertQueryHandler;
import com.bone.system.application.query.dto.AlertEventDTO;
import com.bone.system.common.result.PageResult;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "AlertEventPageQuery",
    description = "标准告警事件分页查询，支持 AI 编排与多租户扩展",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class AlertEventPageQueryUseCase implements UseCaseExecutor<int[], PageResult<AlertEventDTO>> {

    private final AlertQueryHandler alertQueryHandler;

    @Override
    public PageResult<AlertEventDTO> execute(int[] params) {
        int pageNum = params[0];
        int pageSize = params[1];
        return alertQueryHandler.pageEvents(pageNum, pageSize);
    }
}
