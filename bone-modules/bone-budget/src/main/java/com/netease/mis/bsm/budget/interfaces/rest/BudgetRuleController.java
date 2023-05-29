package com.netease.mis.bsm.budget.interfaces.rest;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.netease.mis.bsm.budget.domain.model.BudgetRule;
import com.netease.mis.bsm.budget.domain.service.BudgetRuleService;
import com.netease.mis.bsm.budget.interfaces.convert.BudgetRuleConvert;
import com.netease.mis.bsm.budget.interfaces.dto.BudgetRuleDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.ParameterStyle;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
* 预算规则DTO
*
* @author 梅山源码
*/
@Tag(name = "系统 - 预算规则管理", description = "系统 - 预算规则管理")
@RestController
@RequestMapping("/budget/rule")
@Validated
public class BudgetRuleController {
    @Resource
    private BudgetRuleService ruleService;

    @Resource
    private BudgetRuleConvert ruleConvert;

    @PostMapping("/create")
    @Operation(summary = "创建预算规则")
    @Parameter(name = "ruleDTO", description = "预算规则信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:rule:create')")
    public Result<Long> create(@Valid @RequestBody BudgetRuleDTO ruleDTO) {
        BudgetRule rule = ruleConvert.toEntity(ruleDTO);
        return Result.ok(ruleService.create(rule).getId());
    }

    @PutMapping("/update")
    @Operation(summary = "修改预算规则")
    @Parameter(name = "ruleDTO", description = "预算规则信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:rule:update')")
    public Result<Boolean> update(@Valid @RequestBody BudgetRuleDTO ruleDTO) {
        BudgetRule rule = ruleConvert.toEntity(ruleDTO);
        ruleService.update(rule);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统预算规则")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:rule:delete')")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        ruleService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @Operation(summary = "批量删除")
    @PreAuthorize("@ss.hasPermission('budget:rule:batchDelete')")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @DeleteMapping(value = "/batchDelete")
    public Result<String> batchDelete(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        this.ruleService.batchDelete(ids);
        return Result.ok("批量删除成功!");
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取预算规则")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:rule:query')")
    public Result<BudgetRuleDTO> get(@PathVariable("id") Long id) {
        BudgetRule rule = ruleService.get(id);
        return Result.ok(ruleConvert.toDTO(rule));
    }

    @GetMapping("/list")
    @Operation(summary = "列表查询预算规则")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:rule:query')")
    public Result<List<BudgetRuleDTO>> list(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        List<BudgetRule> list = ruleService.list(ids);
        return Result.ok(ruleConvert.toDTOList(list));
    }

    /**
     * 分页查询
     * @paramruleDTO 查询对象
     * @param pageable 注传参格式： size=10&page=0 &sort=id,asc&sort=name,desc
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询预算规则")
    @Parameters({
            @Parameter(name = "ruleDTO", description = "查询对象", required = false),
            @Parameter(name = "pageable", description = "前页参数", required = true)
    })
    @PreAuthorize("@ss.hasPermission('budget:rule:query')")
    public Result<PageResult<BudgetRuleDTO>> pageable(BudgetRuleDTO ruleDTO, @PageableDefault(size = 15) Pageable pageable) {
        BudgetRule rule = ruleConvert.toEntity(ruleDTO);
        PageResult<BudgetRule> pageResult = ruleService.page(rule, pageable);
        return Result.ok(ruleConvert.toPageResult(pageResult));
    }

    @GetMapping("/export")
    @Operation(summary = "导出预算规则Excel")
    @Parameter(name = "ruleDTO", description = "查询参数", required = true)
    @Parameter(name = "response", description = "HttpServletResponse", required = true)
    @PreAuthorize("@ss.hasPermission('budget:rule:export')")
    public void export(BudgetRuleDTO ruleDTO,
                       HttpServletResponse response) throws IOException {
        BudgetRule rule = ruleConvert.toEntity(ruleDTO);
        List<BudgetRule> list = ruleService.list(rule);
        List<BudgetRuleDTO> result = ruleConvert.toDTOList(list);
        // 导出 Excel
        //List<BudgetRuleDTO> datas = BudgetRuleConvert.toDTOList(list);
        //ExcelUtils.write(response, "BudgetRule.xls", "数据", BudgetRuleExcelDTO.class, datas);
    }
}
