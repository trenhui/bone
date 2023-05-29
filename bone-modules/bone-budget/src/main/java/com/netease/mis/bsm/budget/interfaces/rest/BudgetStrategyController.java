package com.netease.mis.bsm.budget.interfaces.rest;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.netease.mis.bsm.budget.domain.model.BudgetStrategy;
import com.netease.mis.bsm.budget.domain.service.BudgetStrategyService;
import com.netease.mis.bsm.budget.interfaces.convert.BudgetStrategyConvert;
import com.netease.mis.bsm.budget.interfaces.dto.BudgetStrategyDTO;
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
* 预算策略DTO
*
* @author 梅山源码
*/
@Tag(name = "系统 - 预算策略管理", description = "系统 - 预算策略管理")
@RestController
@RequestMapping("/budget/strategy")
@Validated
public class BudgetStrategyController {
    @Resource
    private BudgetStrategyService strategyService;

    @Resource
    private BudgetStrategyConvert strategyConvert;

    @PostMapping("/create")
    @Operation(summary = "创建预算策略")
    @Parameter(name = "strategyDTO", description = "预算策略信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:strategy:create')")
    public Result<Long> create(@Valid @RequestBody BudgetStrategyDTO strategyDTO) {
        BudgetStrategy strategy = strategyConvert.toEntity(strategyDTO);
        return Result.ok(strategyService.create(strategy).getId());
    }

    @PutMapping("/update")
    @Operation(summary = "修改预算策略")
    @Parameter(name = "strategyDTO", description = "预算策略信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:strategy:update')")
    public Result<Boolean> update(@Valid @RequestBody BudgetStrategyDTO strategyDTO) {
        BudgetStrategy strategy = strategyConvert.toEntity(strategyDTO);
        strategyService.update(strategy);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统预算策略")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:strategy:delete')")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        strategyService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @Operation(summary = "批量删除")
    @PreAuthorize("@ss.hasPermission('budget:strategy:batchDelete')")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @DeleteMapping(value = "/batchDelete")
    public Result<String> batchDelete(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        this.strategyService.batchDelete(ids);
        return Result.ok("批量删除成功!");
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取预算策略")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:strategy:query')")
    public Result<BudgetStrategyDTO> get(@PathVariable("id") Long id) {
        BudgetStrategy strategy = strategyService.get(id);
        return Result.ok(strategyConvert.toDTO(strategy));
    }

    @GetMapping("/list")
    @Operation(summary = "列表查询预算策略")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:strategy:query')")
    public Result<List<BudgetStrategyDTO>> list(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        List<BudgetStrategy> list = strategyService.list(ids);
        return Result.ok(strategyConvert.toDTOList(list));
    }

    /**
     * 分页查询
     * @paramstrategyDTO 查询对象
     * @param pageable 注传参格式： size=10&page=0 &sort=id,asc&sort=name,desc
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询预算策略")
    @Parameters({
            @Parameter(name = "strategyDTO", description = "查询对象", required = false),
            @Parameter(name = "pageable", description = "前页参数", required = true)
    })
    @PreAuthorize("@ss.hasPermission('budget:strategy:query')")
    public Result<PageResult<BudgetStrategyDTO>> pageable(BudgetStrategyDTO strategyDTO, @PageableDefault(size = 15) Pageable pageable) {
        BudgetStrategy strategy = strategyConvert.toEntity(strategyDTO);
        PageResult<BudgetStrategy> pageResult = strategyService.page(strategy, pageable);
        return Result.ok(strategyConvert.toPageResult(pageResult));
    }

    @GetMapping("/export")
    @Operation(summary = "导出预算策略Excel")
    @Parameter(name = "strategyDTO", description = "查询参数", required = true)
    @Parameter(name = "response", description = "HttpServletResponse", required = true)
    @PreAuthorize("@ss.hasPermission('budget:strategy:export')")
    public void export(BudgetStrategyDTO strategyDTO,
                       HttpServletResponse response) throws IOException {
        BudgetStrategy strategy = strategyConvert.toEntity(strategyDTO);
        List<BudgetStrategy> list = strategyService.list(strategy);
        List<BudgetStrategyDTO> result = strategyConvert.toDTOList(list);
        // 导出 Excel
        //List<BudgetStrategyDTO> datas = BudgetStrategyConvert.toDTOList(list);
        //ExcelUtils.write(response, "BudgetStrategy.xls", "数据", BudgetStrategyExcelDTO.class, datas);
    }
}
