package com.netease.mis.bsm.budget.interfaces.rest;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.netease.mis.bsm.budget.domain.model.OperationConfig;
import com.netease.mis.bsm.budget.domain.service.OperationConfigService;
import com.netease.mis.bsm.budget.interfaces.convert.OperationConfigConvert;
import com.netease.mis.bsm.budget.interfaces.dto.OperationConfigDTO;
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
* 预算操作配置DTO
*
* @author 梅山源码
*/
@Tag(name = "系统 - 预算操作配置管理", description = "系统 - 预算操作配置管理")
@RestController
@RequestMapping("/budget/operationConfig")
@Validated
public class OperationConfigController {
    @Resource
    private OperationConfigService operationConfigService;

    @Resource
    private OperationConfigConvert operationConfigConvert;

    @PostMapping("/create")
    @Operation(summary = "创建预算操作配置")
    @Parameter(name = "operationConfigDTO", description = "预算操作配置信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:operationConfig:create')")
    public Result<Long> create(@Valid @RequestBody OperationConfigDTO operationConfigDTO) {
        OperationConfig operationConfig = operationConfigConvert.toEntity(operationConfigDTO);
        return Result.ok(operationConfigService.create(operationConfig).getId());
    }

    @PutMapping("/update")
    @Operation(summary = "修改预算操作配置")
    @Parameter(name = "operationConfigDTO", description = "预算操作配置信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:operationConfig:update')")
    public Result<Boolean> update(@Valid @RequestBody OperationConfigDTO operationConfigDTO) {
        OperationConfig operationConfig = operationConfigConvert.toEntity(operationConfigDTO);
        operationConfigService.update(operationConfig);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统预算操作配置")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:operationConfig:delete')")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        operationConfigService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @Operation(summary = "批量删除")
    @PreAuthorize("@ss.hasPermission('budget:operationConfig:batchDelete')")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @DeleteMapping(value = "/batchDelete")
    public Result<String> batchDelete(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        this.operationConfigService.batchDelete(ids);
        return Result.ok("批量删除成功!");
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取预算操作配置")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:operationConfig:query')")
    public Result<OperationConfigDTO> get(@PathVariable("id") Long id) {
        OperationConfig operationConfig = operationConfigService.get(id);
        return Result.ok(operationConfigConvert.toDTO(operationConfig));
    }

    @GetMapping("/list")
    @Operation(summary = "列表查询预算操作配置")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:operationConfig:query')")
    public Result<List<OperationConfigDTO>> list(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        List<OperationConfig> list = operationConfigService.list(ids);
        return Result.ok(operationConfigConvert.toDTOList(list));
    }

    /**
     * 分页查询
     * @paramoperationConfigDTO 查询对象
     * @param pageable 注传参格式： size=10&page=0 &sort=id,asc&sort=name,desc
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询预算操作配置")
    @Parameters({
            @Parameter(name = "operationConfigDTO", description = "查询对象", required = false),
            @Parameter(name = "pageable", description = "前页参数", required = true)
    })
    @PreAuthorize("@ss.hasPermission('budget:operationConfig:query')")
    public Result<PageResult<OperationConfigDTO>> pageable(OperationConfigDTO operationConfigDTO, @PageableDefault(size = 15) Pageable pageable) {
        OperationConfig operationConfig = operationConfigConvert.toEntity(operationConfigDTO);
        PageResult<OperationConfig> pageResult = operationConfigService.page(operationConfig, pageable);
        return Result.ok(operationConfigConvert.toPageResult(pageResult));
    }

    @GetMapping("/export")
    @Operation(summary = "导出预算操作配置Excel")
    @Parameter(name = "operationConfigDTO", description = "查询参数", required = true)
    @Parameter(name = "response", description = "HttpServletResponse", required = true)
    @PreAuthorize("@ss.hasPermission('budget:operationConfig:export')")
    public void export(OperationConfigDTO operationConfigDTO,
                       HttpServletResponse response) throws IOException {
        OperationConfig operationConfig = operationConfigConvert.toEntity(operationConfigDTO);
        List<OperationConfig> list = operationConfigService.list(operationConfig);
        List<OperationConfigDTO> result = operationConfigConvert.toDTOList(list);
        // 导出 Excel
        //List<OperationConfigDTO> datas = OperationConfigConvert.toDTOList(list);
        //ExcelUtils.write(response, "OperationConfig.xls", "数据", OperationConfigExcelDTO.class, datas);
    }
}
