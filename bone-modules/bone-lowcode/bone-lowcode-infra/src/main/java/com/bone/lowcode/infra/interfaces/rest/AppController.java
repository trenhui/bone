package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.bone.lowcode.infra.domain.model.App;
import com.bone.lowcode.infra.domain.service.AppService;
import com.bone.lowcode.infra.interfaces.convert.AppConvert;
import com.bone.lowcode.infra.interfaces.dto.AppDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.ParameterStyle;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * @author renhui.trh
 */
@Tag(name = "系统 - 应用管理", description = "系统 - 应用管理")
@RestController
@RequestMapping("/lowcode/infra/app")
@Validated
public class AppController {
    @Resource
    private AppService appService;

    @Resource
    private AppConvert appConvert;

    @PostMapping("/create")
    @Operation(summary = "创建应用")
    @Parameter(name = "appDTO", description = "应用信息", required = true)
    @PreAuthorize("@ss.hasPermission('system:app:create')")
    public Result<Long> create(@Valid @RequestBody AppDTO appDTO) {
        App app = appConvert.toEntity(appDTO);
        return Result.ok(appService.create(app).getId());
    }

    @PutMapping("/update")
    @Operation(summary = "修改应用")
    @Parameter(name = "appDTO", description = "应用信息", required = true)
    @PreAuthorize("@ss.hasPermission('system:app:update')")
    public Result<Boolean> update(@Valid @RequestBody AppDTO appDTO) {
        App app = appConvert.toEntity(appDTO);
        appService.update(app);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统应用")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('system:app:delete')")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        appService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @Operation(summary = "批量删除")
    @PreAuthorize("@ss.hasPermission('system:app:batchDelete')")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @DeleteMapping(value = "/batchDelete")
    public Result<String> batchDelete(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        this.appService.batchDelete(ids);
        return Result.ok("批量删除成功!");
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取应用")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('system:app:query')")
    public Result<AppDTO> get(@PathVariable("id") Long id) {
        App app = appService.get(id);
        return Result.ok(appConvert.toDTO(app));
    }

    @GetMapping("/list")
    @Operation(summary = "列表查询应用")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('system:app:query')")
    public Result<List<AppDTO>> list(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        List<App> list = appService.list(ids);
        return Result.ok(appConvert.toDTOList(list));
    }

    /**
     * 分页查询
     * @param appDTO 查询对象
     * @param pageable 注传参格式： size=10&page=0 &sort=id,asc&sort=name,desc
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询应用")
    @Parameters({
            @Parameter(name = "appDTO", description = "查询对象", required = false),
            @Parameter(name = "pageable", description = "前页参数", required = true)
    })
    @PreAuthorize("@ss.hasPermission('system:app:query')")
    public Result<PageResult<AppDTO>> pageable(AppDTO appDTO, @PageableDefault(size = 15) Pageable pageable) {
        App app = appConvert.toEntity(appDTO);
        PageResult<App> pageResult = appService.page(app, pageable);
        return Result.ok(appConvert.toPageResult(pageResult));
    }

    @GetMapping("/export")
    @Operation(summary = "导出应用Excel")
    @Parameter(name = "appDTO", description = "查询参数", required = true)
    @Parameter(name = "response", description = "HttpServletResponse", required = true)
    @PreAuthorize("@ss.hasPermission('system:app:export')")
    public void export(AppDTO appDTO,
                       HttpServletResponse response) throws IOException {
        App app = appConvert.toEntity(appDTO);
        List<App> list = appService.list(app);
        List<AppDTO> result = appConvert.toDTOList(list);
        // 导出 Excel
        //List<AppExcelVO> datas = AppConvert.INSTANCE.convertList02(list);
        //ExcelUtils.write(response, "系统应用.xls", "数据", AppExcelVO.class, datas);
    }
}
