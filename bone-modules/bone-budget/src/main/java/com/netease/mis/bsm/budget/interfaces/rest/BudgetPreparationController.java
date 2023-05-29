package com.netease.mis.bsm.budget.interfaces.rest;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.netease.mis.bsm.budget.domain.model.BudgetPreparation;
import com.netease.mis.bsm.budget.domain.service.BudgetPreparationService;
import com.netease.mis.bsm.budget.interfaces.convert.BudgetPreparationConvert;
import com.netease.mis.bsm.budget.interfaces.dto.BudgetPreparationDTO;
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
* 预算编制DTO
*
* @author 梅山源码
*/
@Tag(name = "系统 - 预算编制管理", description = "系统 - 预算编制管理")
@RestController
@RequestMapping("/budget/preparation")
@Validated
public class BudgetPreparationController {
    @Resource
    private BudgetPreparationService preparationService;

    @Resource
    private BudgetPreparationConvert preparationConvert;

    @PostMapping("/create")
    @Operation(summary = "创建预算编制")
    @Parameter(name = "preparationDTO", description = "预算编制信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:preparation:create')")
    public Result<Long> create(@Valid @RequestBody BudgetPreparationDTO preparationDTO) {
        BudgetPreparation preparation = preparationConvert.toEntity(preparationDTO);
        return Result.ok(preparationService.create(preparation).getId());
    }

    @PutMapping("/update")
    @Operation(summary = "修改预算编制")
    @Parameter(name = "preparationDTO", description = "预算编制信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:preparation:update')")
    public Result<Boolean> update(@Valid @RequestBody BudgetPreparationDTO preparationDTO) {
        BudgetPreparation preparation = preparationConvert.toEntity(preparationDTO);
        preparationService.update(preparation);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统预算编制")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:preparation:delete')")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        preparationService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @Operation(summary = "批量删除")
    @PreAuthorize("@ss.hasPermission('budget:preparation:batchDelete')")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @DeleteMapping(value = "/batchDelete")
    public Result<String> batchDelete(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        this.preparationService.batchDelete(ids);
        return Result.ok("批量删除成功!");
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取预算编制")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:preparation:query')")
    public Result<BudgetPreparationDTO> get(@PathVariable("id") Long id) {
        BudgetPreparation preparation = preparationService.get(id);
        return Result.ok(preparationConvert.toDTO(preparation));
    }

    @GetMapping("/list")
    @Operation(summary = "列表查询预算编制")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:preparation:query')")
    public Result<List<BudgetPreparationDTO>> list(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        List<BudgetPreparation> list = preparationService.list(ids);
        return Result.ok(preparationConvert.toDTOList(list));
    }

    /**
     * 分页查询
     * @parampreparationDTO 查询对象
     * @param pageable 注传参格式： size=10&page=0 &sort=id,asc&sort=name,desc
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询预算编制")
    @Parameters({
            @Parameter(name = "preparationDTO", description = "查询对象", required = false),
            @Parameter(name = "pageable", description = "前页参数", required = true)
    })
    @PreAuthorize("@ss.hasPermission('budget:preparation:query')")
    public Result<PageResult<BudgetPreparationDTO>> pageable(BudgetPreparationDTO preparationDTO, @PageableDefault(size = 15) Pageable pageable) {
        BudgetPreparation preparation = preparationConvert.toEntity(preparationDTO);
        PageResult<BudgetPreparation> pageResult = preparationService.page(preparation, pageable);
        return Result.ok(preparationConvert.toPageResult(pageResult));
    }

    @GetMapping("/export")
    @Operation(summary = "导出预算编制Excel")
    @Parameter(name = "preparationDTO", description = "查询参数", required = true)
    @Parameter(name = "response", description = "HttpServletResponse", required = true)
    @PreAuthorize("@ss.hasPermission('budget:preparation:export')")
    public void export(BudgetPreparationDTO preparationDTO,
                       HttpServletResponse response) throws IOException {
        BudgetPreparation preparation = preparationConvert.toEntity(preparationDTO);
        List<BudgetPreparation> list = preparationService.list(preparation);
        List<BudgetPreparationDTO> result = preparationConvert.toDTOList(list);
        // 导出 Excel
        //List<BudgetPreparationDTO> datas = BudgetPreparationConvert.toDTOList(list);
        //ExcelUtils.write(response, "BudgetPreparation.xls", "数据", BudgetPreparationExcelDTO.class, datas);
    }
}
