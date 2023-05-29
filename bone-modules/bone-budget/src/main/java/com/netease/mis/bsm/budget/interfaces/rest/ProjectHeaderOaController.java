package com.netease.mis.bsm.budget.interfaces.rest;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.netease.mis.bsm.budget.domain.model.ProjectHeaderOa;
import com.netease.mis.bsm.budget.domain.service.ProjectHeaderOaService;
import com.netease.mis.bsm.budget.interfaces.convert.ProjectHeaderOaConvert;
import com.netease.mis.bsm.budget.interfaces.dto.ProjectHeaderOaDTO;
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
* OA项目单头DTO
*
* @author 梅山源码
*/
@Tag(name = "系统 - OA项目单头管理", description = "系统 - OA项目单头管理")
@RestController
@RequestMapping("/budget/projectHeaderOa")
@Validated
public class ProjectHeaderOaController {
    @Resource
    private ProjectHeaderOaService projectHeaderOaService;

    @Resource
    private ProjectHeaderOaConvert projectHeaderOaConvert;

    @PostMapping("/create")
    @Operation(summary = "创建OA项目单头")
    @Parameter(name = "projectHeaderOaDTO", description = "OA项目单头信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:projectHeaderOa:create')")
    public Result<Long> create(@Valid @RequestBody ProjectHeaderOaDTO projectHeaderOaDTO) {
        ProjectHeaderOa projectHeaderOa = projectHeaderOaConvert.toEntity(projectHeaderOaDTO);
        return Result.ok(projectHeaderOaService.create(projectHeaderOa).getId());
    }

    @PutMapping("/update")
    @Operation(summary = "修改OA项目单头")
    @Parameter(name = "projectHeaderOaDTO", description = "OA项目单头信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:projectHeaderOa:update')")
    public Result<Boolean> update(@Valid @RequestBody ProjectHeaderOaDTO projectHeaderOaDTO) {
        ProjectHeaderOa projectHeaderOa = projectHeaderOaConvert.toEntity(projectHeaderOaDTO);
        projectHeaderOaService.update(projectHeaderOa);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统OA项目单头")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:projectHeaderOa:delete')")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        projectHeaderOaService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @Operation(summary = "批量删除")
    @PreAuthorize("@ss.hasPermission('budget:projectHeaderOa:batchDelete')")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @DeleteMapping(value = "/batchDelete")
    public Result<String> batchDelete(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        this.projectHeaderOaService.batchDelete(ids);
        return Result.ok("批量删除成功!");
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取OA项目单头")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:projectHeaderOa:query')")
    public Result<ProjectHeaderOaDTO> get(@PathVariable("id") Long id) {
        ProjectHeaderOa projectHeaderOa = projectHeaderOaService.get(id);
        return Result.ok(projectHeaderOaConvert.toDTO(projectHeaderOa));
    }

    @GetMapping("/list")
    @Operation(summary = "列表查询OA项目单头")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:projectHeaderOa:query')")
    public Result<List<ProjectHeaderOaDTO>> list(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        List<ProjectHeaderOa> list = projectHeaderOaService.list(ids);
        return Result.ok(projectHeaderOaConvert.toDTOList(list));
    }

    /**
     * 分页查询
     * @paramprojectHeaderOaDTO 查询对象
     * @param pageable 注传参格式： size=10&page=0 &sort=id,asc&sort=name,desc
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询OA项目单头")
    @Parameters({
            @Parameter(name = "projectHeaderOaDTO", description = "查询对象", required = false),
            @Parameter(name = "pageable", description = "前页参数", required = true)
    })
    @PreAuthorize("@ss.hasPermission('budget:projectHeaderOa:query')")
    public Result<PageResult<ProjectHeaderOaDTO>> pageable(ProjectHeaderOaDTO projectHeaderOaDTO, @PageableDefault(size = 15) Pageable pageable) {
        ProjectHeaderOa projectHeaderOa = projectHeaderOaConvert.toEntity(projectHeaderOaDTO);
        PageResult<ProjectHeaderOa> pageResult = projectHeaderOaService.page(projectHeaderOa, pageable);
        return Result.ok(projectHeaderOaConvert.toPageResult(pageResult));
    }

    @GetMapping("/export")
    @Operation(summary = "导出OA项目单头Excel")
    @Parameter(name = "projectHeaderOaDTO", description = "查询参数", required = true)
    @Parameter(name = "response", description = "HttpServletResponse", required = true)
    @PreAuthorize("@ss.hasPermission('budget:projectHeaderOa:export')")
    public void export(ProjectHeaderOaDTO projectHeaderOaDTO,
                       HttpServletResponse response) throws IOException {
        ProjectHeaderOa projectHeaderOa = projectHeaderOaConvert.toEntity(projectHeaderOaDTO);
        List<ProjectHeaderOa> list = projectHeaderOaService.list(projectHeaderOa);
        List<ProjectHeaderOaDTO> result = projectHeaderOaConvert.toDTOList(list);
        // 导出 Excel
        //List<ProjectHeaderOaDTO> datas = ProjectHeaderOaConvert.toDTOList(list);
        //ExcelUtils.write(response, "ProjectHeaderOa.xls", "数据", ProjectHeaderOaExcelDTO.class, datas);
    }
}
