package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.dto.table.CreateAggregateRuleDTO;
import com.bone.lowcode.infra.application.dto.table.UpdateAggregateRuleDTO;
import com.bone.lowcode.infra.application.dto.table.UpdateTableDTO;
import com.bone.lowcode.infra.application.service.TableApplicationService;
import com.bone.lowcode.infra.application.vo.table.*;
import com.bone.lowcode.infra.domain.model.DataSummaryRule;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @Author fhmdf
 * @create 2024/7/25 14:50
 */
@RestController
@RequestMapping("/table")
@Validated
public class TableController {

    @Autowired
    private TableApplicationService tableApplicationService;

    /**
     * 获取指定字段所在页面的表格
     */
    @GetMapping("/getByFieldId")
    public Result getByFieldId(@NotNull @RequestParam(value = "fieldId") Long fieldId) {
        List<TableVO1> voList = tableApplicationService.getByFieldId(fieldId);
        return Result.ok(voList);
    }


    /**
     * 获取表格的详情
     */
    @GetMapping("/get")
    public Result getById(@NotNull @RequestParam(value = "tableId") Long tableId) {
        TableVo vo = tableApplicationService.getById(tableId);
        return Result.ok(vo);
    }

    /**
     * 更新表格的详情
     */
    @PostMapping("/update")
    public Result updateTableById(@RequestBody UpdateTableDTO dto) {
        boolean flag = tableApplicationService.updateTableById(dto);
        if (flag) return Result.ok();
        else return Result.error("更新失败！");
    }

    /**
     * 获取表格的数据汇总规则
     */
    @GetMapping("/getDataSummaryRuleByTableId")
    public Result getDataSummaryRuleByTableId(@NotNull @RequestParam(value = "tableId") Long tableId) {
        List<DataSummaryRule> voList = tableApplicationService.getDataSummaryRuleByTableId(tableId);
        return Result.ok(voList);
    }

    /**
     * 获取表格中的字段(文本类型、数字类型)
     */
    @GetMapping("/getTextNumberField")
    public Result getDataSummaryField(@NotNull @RequestParam(value = "tableId") Long tableId) {
        List<FieldSimpleInfo> voList = tableApplicationService.getDataSummaryField(tableId);
        return Result.ok(voList);
    }


    /**
     * 查询某table下的所有字段
     */
    @GetMapping("/fieldList")
    public Result getFieldByTableId(@RequestParam("tableId") Long tableId) {
        List<TableFieldVO> voList = tableApplicationService.getFieldByTableId(tableId);
        return Result.ok(voList);
    }

    /**
     * 创建表格分组聚合规则
     */
    @PostMapping("/createAggregateRule")
    public Result createAggregateRule(@RequestBody CreateAggregateRuleDTO dto) {
        boolean flag = tableApplicationService.createAggregateRule(dto);
        if (flag) return Result.ok();
        else return Result.error("创建失败！");
    }

    /**
     * 查询某table下的分组聚合规则
     */
    @GetMapping("/getAggregateRule")
    public Result getAggregateRule(@RequestParam("tableId") Long tableId) {
        List<AggregateRuleVO> voList = tableApplicationService.getAggregateRule(tableId);
        return Result.ok(voList);
    }

    /**
     * 更新表格分组聚合规则
     */
    @PostMapping("/updateAggregateRule")
    public Result updateAggregateRule(@RequestBody UpdateAggregateRuleDTO dto) {
        boolean flag = tableApplicationService.updateAggregateRule(dto);
        if (flag) return Result.ok();
        else return Result.error("更新失败！");
    }

    /**
     * 删除某table下的分组聚合规则
     */
    @GetMapping("/deleteAggregateRule")
    public Result deleteAggregateRule(@RequestParam("aggregateRuledId") Long aggregateRuledId) {
        boolean flag = tableApplicationService.deleteAggregateRule(aggregateRuledId);
        if (flag) return Result.ok();
        else return Result.error("删除失败！");
    }

    /**
     * 获取指定页面的表格
     */
//    @GetMapping("/getByPageCode")
    public Result getByPageCode(@RequestParam("pageCode") String pageCode,
                                @RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        List<TableVO1> voList = tableApplicationService.getByPageCode(pageCode, bizIdentityCode);
        return Result.ok(voList);
    }
}
