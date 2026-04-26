package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.dto.tableRule.*;
import com.bone.lowcode.infra.application.service.TableRuleApplicationService;
import com.bone.lowcode.infra.application.vo.tableRule.*;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/tableRule")
@Validated
public class TableRuleController {

    @Autowired
    private TableRuleApplicationService tableRuleApplicationService;

    /**
     * 表格行内动态规则-创建
     */
    @PostMapping("/edit/createTableRowEditRule")
    public Result createTableRowEditRule(@RequestBody CreateTableRowEditRuleDTO dto) {
        boolean flag = tableRuleApplicationService.createTableRowEditRule(dto);
        if (flag) return Result.ok("创建成功");
        else return Result.error("创建失败！");
    }

    /**
     * 表格行内动态规则-查询指定表格的行内动态规则
     */
    @GetMapping("/edit/getTableRowEditRule")
    public Result getRowRule(@NotNull @RequestParam(value = "tableId") Long tableId) {
        List<TableRowEditRuleVO> voList = tableRuleApplicationService.getTableRowEditRule(tableId);
        return Result.ok(voList);
    }

    /**
     * 表格行内动态规则-修改
     */
    @PostMapping("/edit/updateTableRowEditRule")
    public Result updateRowRule(@RequestBody UpdateRowRuleDTO dto) {
        boolean flag = tableRuleApplicationService.updateRowRule(dto);
        if (flag) return Result.ok("修改成功！");
        else return Result.error("修改失败！");
    }

    /**
     * 表格行内动态规则-删除
     */
    @GetMapping("/edit/deleteTableRowEditRule")
    public Result deleteRowRule(@NotNull @RequestParam(value = "id") Long id) {
        boolean flag = tableRuleApplicationService.deleteRowRule(id);
        if (flag) return Result.ok("删除失成功！");
        else return Result.error("删除失败！");
    }

    /**
     * 表格关系-根据"多"方表格id查询"一"方表格
     */
    @GetMapping("/edit/getTargetTableByCurrentTableId")
    public Result getTargetTable(@NotNull @RequestParam(value = "tableId") Long tableId) {
        List<GetTargetTableVO> voList = tableRuleApplicationService.getTargetTable(tableId);
        return Result.ok(voList);
    }

    /**
     * 表间动态规则--创建
     */
    @PostMapping("/edit/createCrossTableDataEditRule")
    public Result createCrossTableDataEditRule(@RequestBody TableDataRelationDTO dto) {
        boolean flag = tableRuleApplicationService.createCrossTableDataEditRule(dto);
        if (flag) return Result.ok("创建成功！");
        else return Result.error("创建失败！");
    }

    /**
     * 表间动态规则-根据表格id查询
     */
    @GetMapping("/edit/getCrossTableDataEditRuleByTableId")
    public Result getCrossTableDataEditRuleByTableId(@NotNull @RequestParam(value = "tableId") Long tableId,
                                                     @NotNull @RequestParam(value = "type") Integer type) {
        //1:TargetTableId 2:CurrentTableId
        List<CrossTableDataEditVO> voList = tableRuleApplicationService.getCrossTableEditRule(tableId, type);
        return Result.ok(voList);
    }

    /**
     * 表间动态规则-更新
     */
    @PostMapping("/edit/updateCrossTableDataEditRule")
    public Result updateCrossTableDataEditRule(@RequestBody UpdateTableDataRelationDTO dto) {
        boolean flag = tableRuleApplicationService.updateCrossTableDataEditRule(dto);
        if (flag) return Result.ok("更新成功！");
        else return Result.error("更新失败！");
    }

    /**
     * 表间动态规则-删除
     */
    @GetMapping("/edit/deleteCrossTableDataEditRule")
    public Result deleteCrossTableDataEditRule(@NotNull @RequestParam(value = "id") Long id) {
        boolean flag = tableRuleApplicationService.deleteCrossTableDataEditRule(id);
        if (flag) return Result.ok("删除成功！");
        else return Result.error("删除失败！");
    }


    /**
     * 表格动态规则-根据page查询两种动态规则
     */
    @GetMapping("/edit/getTableEditRule")
    public Result getTableEditRule(@RequestParam("pageCode") String pageCode,
                                   @RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        List<EditTableRuleVO> voList = tableRuleApplicationService.getTableEditRule(pageCode, bizIdentityCode);
        return Result.ok(voList);
    }

    /**
     * 表格动态规则-批量更新序号
     */
    @PostMapping("/edit/updateSequence")
    public Result updateSequence(@Validated @RequestBody List<UpdateSequenceDTO> paramList) {
        try {
            tableRuleApplicationService.updateSequence(paramList);
            return Result.ok("更新完成");
        } catch (Exception e) {
            log.error("批量更新表格动态规则的序号发生异常", e);
            return Result.error("批量更新表格动态规则的序号发生异常,msg:" + e.getMessage());
        }
    }

    /**
     * 表格行内字段校验规则-创建
     */
    @PostMapping("/verify/createTableRowVerifyRule")
    public Result createTableRowVerifyRule(@RequestBody CreateTableRowVerifyDTO dto) {
        boolean flag = tableRuleApplicationService.createTableRowVerifyRule(dto);
        if (flag) return Result.ok("创建成功！");
        else return Result.error("创建失败！");
    }

    /**
     * 表格行内字段校验规则-根据表格id查询
     */
    @GetMapping("/verify/getTableRowVerifyRuleByTableId")
    public Result getTableRowVerifyRuleByTableId(@NotNull @RequestParam(value = "tableId") Long tableId) {
        List<TableRowVerifyRuleVO> voList = tableRuleApplicationService.getTableRowVerifyRuleByTableId(tableId);
        return Result.ok(voList);
    }

    /**
     * 表格行内字段校验规则-更新
     */
    @PostMapping("/verify/updateTableRowVerifyRule")
    public Result updateTableRowVerifyRule(@RequestBody UpdateTableRowVerifyDTO dto) {
        boolean flag = tableRuleApplicationService.updateTableRowVerifyRule(dto);
        if (flag) return Result.ok("更新成功！");
        else return Result.error("更新失败！");
    }

    /**
     * 表格行内字段校验规则-根据id删除
     */
    @GetMapping("/verify/deleteTableRowVerifyRuleById")
    public Result deleteRowVerifyRuleById(@NotNull @RequestParam(value = "id") Long id) {
        boolean flag = tableRuleApplicationService.deleteRowVerifyRuleById(id);
        if (flag) return Result.ok("删除成功！");
        else return Result.error("删除失败！");
    }

    /**
     * 数据一对多关系的两个表格-根据表格id查询表格关系及关联字段
     */
    @GetMapping("/getTableRelationByTargetTableId")
    public Result getTableRelationByTargetTableId(@NotNull @RequestParam(value = "tableId") Long tableId,
                                                  @NotNull @RequestParam(value = "type") Integer type) {
        //type: 1->TargetTable 2->CurrentTable
        List<TableRelationVO> voList = tableRuleApplicationService.getTableRelationByTargetTableId(tableId, type);
        return Result.ok(voList);
    }

    /**
     * 跨表格字段校验规则-创建
     */
    @PostMapping("/verify/createCrossTableDataVerifyRule")
    public Result createCrossTableDataVerifyRule(@RequestBody CreateCrossTableDataVerifyRuleDTO dto) {
        boolean flag = tableRuleApplicationService.createCrossTableDataVerifyRule(dto);
        if (flag) return Result.ok("创建成功！");
        else return Result.error("创建失败！");
    }

    /**
     * 跨表格字段校验规则-查询
     */
    @GetMapping("/verify/getCrossTableDataVerifyRule")
    public Result getCrossTableDataVerifyRule(@NotNull @RequestParam(value = "tableId") Long tableId,
                                              @NotNull @RequestParam(value = "type") Integer type) {
        //type: 1->TargetTable 2->CurrentTable
        List<CrossTableDataVerifyRuleVO> voList = tableRuleApplicationService.getCrossTableDataVerifyRule(tableId, type);
        return Result.ok(voList);
    }

    /**
     * 跨表格字段校验规则-更新
     */
    @PostMapping("/verify/updateCrossTableDataVerifyRule")
    public Result updateCrossTableDataVerifyRule(@RequestBody UpdateCrossTableDataVerifyRuleDTO dto) {
        boolean flag = tableRuleApplicationService.updateCrossTableDataVerifyRule(dto);
        if (flag) return Result.ok("更新成功！");
        else return Result.error("更新失败！");
    }

    /**
     * 跨表格字段校验规则-删除
     */
    @GetMapping("/verify/deleteCrossTableDataVerifyRule")
    public Result deleteCrossTableDataVerifyRule(@NotNull @RequestParam(value = "id") Long id) {
        boolean flag = tableRuleApplicationService.deleteCrossTableDataVerifyRule(id);
        if (flag) return Result.ok("删除成功！");
        else return Result.error("删除失败！");
    }

    /**
     * 表格保存校验规则-根据page查询两种校验规则
     */
    @GetMapping("/verify/getTableVerifyRule")
    public Result getTableVerifyRule(@RequestParam("pageCode") String pageCode,
                                     @RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        List<VerifyTableRuleVO> voList = tableRuleApplicationService.getTableVerifyRule(pageCode, bizIdentityCode);
        return Result.ok(voList);
    }

    /**
     * 根据tableId查询四种规则
     */
    @GetMapping("/getFourRule")
    public Result getFourRule(@NotNull @RequestParam(value = "tableId") Long tableId) {
        FourTableRuleVO vo = tableRuleApplicationService.getFourRule(tableId);
        return Result.ok(vo);
    }
}
