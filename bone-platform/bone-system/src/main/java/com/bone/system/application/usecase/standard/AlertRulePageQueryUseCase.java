package com.bone.system.application.usecase.standard;

import com.bone.system.application.query.qry.AlertRulePageQry;
import com.bone.system.application.query.handler.AlertQueryHandler;
import com.bone.system.application.query.dto.AlertRuleDTO;
import com.bone.system.common.result.PageResult;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@UseCase(
    name = "AlertRulePageQuery",
    description = "标准告警规则分页查询，支持 AI 编排与多租户扩展",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class AlertRulePageQueryUseCase implements UseCaseExecutor<AlertRulePageQry, PageResult<AlertRuleDTO>> {

    private final AlertQueryHandler alertQueryHandler;

    @Override
    public PageResult<AlertRuleDTO> execute(AlertRulePageQry qry) {
        return alertQueryHandler.pageRules(qry);
    }
}
