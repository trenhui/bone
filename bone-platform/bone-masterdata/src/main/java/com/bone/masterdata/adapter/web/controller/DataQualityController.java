package com.bone.masterdata.adapter.web.controller;

import com.bone.masterdata.application.command.cmd.CreateDataQualityRuleCommand;
import com.bone.masterdata.application.command.cmd.PerformDataQualityCheckCommand;
import com.bone.masterdata.application.command.handler.CreateDataQualityRuleHandler;
import com.bone.masterdata.application.command.handler.PerformDataQualityCheckHandler;
import com.bone.masterdata.application.query.dto.DataQualityRuleDTO;
import com.bone.masterdata.application.query.dto.QualityReportDTO;
import com.bone.masterdata.application.query.handler.DataQualityRuleListQueryHandler;
import com.bone.masterdata.application.query.handler.GetQualityReportQueryHandler;
import com.bone.masterdata.application.query.qry.DataQualityRuleListQuery;
import com.bone.core.result.ApiResponse;
import com.bone.core.web.PlatformApiPaths;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(PlatformApiPaths.MASTERDATA_V1 + "/quality")
@RequiredArgsConstructor
public class DataQualityController {
    private final CreateDataQualityRuleHandler createRuleHandler;
    private final DataQualityRuleListQueryHandler ruleListQueryHandler;
    private final PerformDataQualityCheckHandler performCheckHandler;
    private final GetQualityReportQueryHandler getQualityReportQueryHandler;

    @PostMapping("/rules")
    public ApiResponse<Long> createRule(@RequestBody CreateDataQualityRuleCommand cmd) {
        Long id = createRuleHandler.handle(cmd);
        return ApiResponse.success(id);
    }

    @GetMapping("/rules")
    public ApiResponse<List<DataQualityRuleDTO>> listRules(DataQualityRuleListQuery qry) {
        List<DataQualityRuleDTO> rules = ruleListQueryHandler.handle(qry);
        return ApiResponse.success(rules);
    }

    @PostMapping("/check")
    public ApiResponse<Long> performCheck(@RequestParam("masterDataEntityId") Long masterDataEntityId) {
        PerformDataQualityCheckCommand cmd = new PerformDataQualityCheckCommand();
        cmd.setMasterDataEntityId(masterDataEntityId);
        Long reportId = performCheckHandler.handle(cmd);
        return ApiResponse.success(reportId);
    }

    @GetMapping("/reports/{id}")
    public ApiResponse<QualityReportDTO> getReport(@PathVariable Long id) {
        return ApiResponse.success(getQualityReportQueryHandler.handle(id));
    }
}