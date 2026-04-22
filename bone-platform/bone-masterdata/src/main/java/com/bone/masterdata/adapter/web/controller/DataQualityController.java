package com.bone.masterdata.adapter.web.controller;

import com.bone.masterdata.application.command.cmd.CreateDataQualityRuleCmd;
import com.bone.masterdata.application.command.cmd.PerformDataQualityCheckCmd;
import com.bone.masterdata.application.command.handler.CreateDataQualityRuleHandler;
import com.bone.masterdata.application.command.handler.PerformDataQualityCheckHandler;
import com.bone.masterdata.application.query.dto.DataQualityRuleDTO;
import com.bone.masterdata.application.query.handler.DataQualityRuleListQueryHandler;
import com.bone.masterdata.application.query.qry.DataQualityRuleListQry;
import com.bone.core.result.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/masterdata/quality")
@RequiredArgsConstructor
public class DataQualityController {
    private final CreateDataQualityRuleHandler createRuleHandler;
    private final DataQualityRuleListQueryHandler ruleListQueryHandler;
    private final PerformDataQualityCheckHandler performCheckHandler;

    @PostMapping("/rules")
    public ApiResponse<Long> createRule(@RequestBody CreateDataQualityRuleCmd cmd) {
        Long id = createRuleHandler.handle(cmd);
        return ApiResponse.success(id);
    }

    @GetMapping("/rules")
    public ApiResponse<List<DataQualityRuleDTO>> listRules(DataQualityRuleListQry qry) {
        List<DataQualityRuleDTO> rules = ruleListQueryHandler.handle(qry);
        return ApiResponse.success(rules);
    }

    @PostMapping("/check")
    public ApiResponse<Long> performCheck(@RequestParam Long masterDataEntityId) {
        PerformDataQualityCheckCmd cmd = new PerformDataQualityCheckCmd();
        cmd.setMasterDataEntityId(masterDataEntityId);
        Long checkId = performCheckHandler.handle(cmd);
        return ApiResponse.success(checkId);
    }

    @GetMapping("/reports/{id}")
    public ApiResponse<String> getReport(@PathVariable Long id) {
        // TODO: 实现获取质量报告的逻辑
        return ApiResponse.success("质量报告内容");
    }
}