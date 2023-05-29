package com.netease.mis.bsm.budget.interfaces.rest;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.netease.mis.bsm.budget.domain.model.BudgetSet;
import com.netease.mis.bsm.budget.domain.service.BudgetSetService;
import com.netease.mis.bsm.budget.interfaces.convert.BudgetSetConvert;
import com.netease.mis.bsm.budget.interfaces.dto.BudgetSetDTO;
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
* 预算设置DTO
*
* @author 梅山源码
*/
@Tag(name = "系统 - 预算设置管理", description = "系统 - 预算设置管理")
@RestController
@RequestMapping("/budget/set")
@Validated
public class BudgetSetController {
    @Resource
    private BudgetSetService setService;

    @Resource
    private BudgetSetConvert setConvert;

    @PostMapping("/create")
    @Operation(summary = "创建预算设置")
    @Parameter(name = "setDTO", description = "预算设置信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:set:create')")
    public Result<Long> create(@Valid @RequestBody BudgetSetDTO setDTO) {
        BudgetSet set = setConvert.toEntity(setDTO);
        return Result.ok(setService.create(set).getId());
    }

    @PutMapping("/update")
    @Operation(summary = "修改预算设置")
    @Parameter(name = "setDTO", description = "预算设置信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:set:update')")
    public Result<Boolean> update(@Valid @RequestBody BudgetSetDTO setDTO) {
        BudgetSet set = setConvert.toEntity(setDTO);
        setService.update(set);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统预算设置")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:set:delete')")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        setService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @Operation(summary = "批量删除")
    @PreAuthorize("@ss.hasPermission('budget:set:batchDelete')")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @DeleteMapping(value = "/batchDelete")
    public Result<String> batchDelete(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        this.setService.batchDelete(ids);
        return Result.ok("批量删除成功!");
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取预算设置")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:set:query')")
    public Result<BudgetSetDTO> get(@PathVariable("id") Long id) {
        BudgetSet set = setService.get(id);
        return Result.ok(setConvert.toDTO(set));
    }

    @GetMapping("/list")
    @Operation(summary = "列表查询预算设置")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:set:query')")
    public Result<List<BudgetSetDTO>> list(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        List<BudgetSet> list = setService.list(ids);
        return Result.ok(setConvert.toDTOList(list));
    }

    /**
     * 分页查询
     * @paramsetDTO 查询对象
     * @param pageable 注传参格式： size=10&page=0 &sort=id,asc&sort=name,desc
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询预算设置")
    @Parameters({
            @Parameter(name = "setDTO", description = "查询对象", required = false),
            @Parameter(name = "pageable", description = "前页参数", required = true)
    })
    @PreAuthorize("@ss.hasPermission('budget:set:query')")
    public Result<PageResult<BudgetSetDTO>> pageable(BudgetSetDTO setDTO, @PageableDefault(size = 15) Pageable pageable) {
        BudgetSet set = setConvert.toEntity(setDTO);
        PageResult<BudgetSet> pageResult = setService.page(set, pageable);
        return Result.ok(setConvert.toPageResult(pageResult));
    }

    @GetMapping("/export")
    @Operation(summary = "导出预算设置Excel")
    @Parameter(name = "setDTO", description = "查询参数", required = true)
    @Parameter(name = "response", description = "HttpServletResponse", required = true)
    @PreAuthorize("@ss.hasPermission('budget:set:export')")
    public void export(BudgetSetDTO setDTO,
                       HttpServletResponse response) throws IOException {
        BudgetSet set = setConvert.toEntity(setDTO);
        List<BudgetSet> list = setService.list(set);
        List<BudgetSetDTO> result = setConvert.toDTOList(list);
        // 导出 Excel
        //List<BudgetSetDTO> datas = BudgetSetConvert.toDTOList(list);
        //ExcelUtils.write(response, "BudgetSet.xls", "数据", BudgetSetExcelDTO.class, datas);
    }
}
