package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.bone.lowcode.infra.domain.model.Tenant;
import com.bone.lowcode.infra.domain.service.TenantService;
import com.bone.lowcode.infra.interfaces.convert.TenantConvert;
import com.bone.lowcode.infra.interfaces.dto.TenantDTO;
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
* 租户DTO
*
* @author 梅山源码
*/
@Tag(name = "系统 - 租户管理", description = "系统 - 租户管理")
@RestController
@RequestMapping("/infra/tenant")
@Validated
public class TenantController {
    @Resource
    private TenantService tenantService;

    @Resource
    private TenantConvert tenantConvert;

    @PostMapping("/create")
    @Operation(summary = "创建租户")
    @Parameter(name = "tenantDTO", description = "租户信息", required = true)
    @PreAuthorize("@ss.hasPermission('infra:tenant:create')")
    public Result<Long> create(@Valid @RequestBody TenantDTO tenantDTO) {
        Tenant tenant = tenantConvert.toEntity(tenantDTO);
        return Result.ok(tenantService.create(tenant).getId());
    }

    @PutMapping("/update")
    @Operation(summary = "修改租户")
    @Parameter(name = "tenantDTO", description = "租户信息", required = true)
    @PreAuthorize("@ss.hasPermission('infra:tenant:update')")
    public Result<Boolean> update(@Valid @RequestBody TenantDTO tenantDTO) {
        Tenant tenant = tenantConvert.toEntity(tenantDTO);
        tenantService.update(tenant);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统租户")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('infra:tenant:delete')")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        tenantService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @Operation(summary = "批量删除")
    @PreAuthorize("@ss.hasPermission('infra:tenant:batchDelete')")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @DeleteMapping(value = "/batchDelete")
    public Result<String> batchDelete(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        this.tenantService.batchDelete(ids);
        return Result.ok("批量删除成功!");
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取租户")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('infra:tenant:query')")
    public Result<TenantDTO> get(@PathVariable("id") Long id) {
        Tenant tenant = tenantService.get(id);
        return Result.ok(tenantConvert.toDTO(tenant));
    }

    @GetMapping("/list")
    @Operation(summary = "列表查询租户")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('infra:tenant:query')")
    public Result<List<TenantDTO>> list(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        List<Tenant> list = tenantService.list(ids);
        return Result.ok(tenantConvert.toDTOList(list));
    }

    /**
     * 分页查询
     * @paramtenantDTO 查询对象
     * @param pageable 注传参格式： size=10&page=0 &sort=id,asc&sort=name,desc
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询租户")
    @Parameters({
            @Parameter(name = "tenantDTO", description = "查询对象", required = false),
            @Parameter(name = "pageable", description = "前页参数", required = true)
    })
    @PreAuthorize("@ss.hasPermission('infra:tenant:query')")
    public Result<PageResult<TenantDTO>> pageable(TenantDTO tenantDTO, @PageableDefault(size = 15) Pageable pageable) {
        Tenant tenant = tenantConvert.toEntity(tenantDTO);
        PageResult<Tenant> pageResult = tenantService.page(tenant, pageable);
        return Result.ok(tenantConvert.toPageResult(pageResult));
    }

    @GetMapping("/export")
    @Operation(summary = "导出租户Excel")
    @Parameter(name = "tenantDTO", description = "查询参数", required = true)
    @Parameter(name = "response", description = "HttpServletResponse", required = true)
    @PreAuthorize("@ss.hasPermission('infra:tenant:export')")
    public void export(TenantDTO tenantDTO,
                       HttpServletResponse response) throws IOException {
        Tenant tenant = tenantConvert.toEntity(tenantDTO);
        List<Tenant> list = tenantService.list(tenant);
        List<TenantDTO> result = tenantConvert.toDTOList(list);
        // 导出 Excel
        //List<TenantDTO> datas = TenantConvert.toDTOList(list);
        //ExcelUtils.write(response, "Tenant.xls", "数据", TenantExcelDTO.class, datas);
    }
}
