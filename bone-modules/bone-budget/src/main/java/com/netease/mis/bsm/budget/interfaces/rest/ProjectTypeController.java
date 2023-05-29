package com.netease.mis.bsm.budget.interfaces.rest;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.netease.mis.bsm.budget.domain.model.ProjectType;
import com.netease.mis.bsm.budget.domain.service.ProjectTypeService;
import com.netease.mis.bsm.budget.interfaces.convert.ProjectTypeConvert;
import com.netease.mis.bsm.budget.interfaces.dto.ProjectTypeDTO;
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
* 项目类型DTO
*
* @author 梅山源码
*/
@Tag(name = "系统 - 项目类型管理", description = "系统 - 项目类型管理")
@RestController
@RequestMapping("/budget/projectType")
@Validated
public class ProjectTypeController {
    @Resource
    private ProjectTypeService projectTypeService;

    @Resource
    private ProjectTypeConvert projectTypeConvert;

    @PostMapping("/create")
    @Operation(summary = "创建项目类型")
    @Parameter(name = "projectTypeDTO", description = "项目类型信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:projectType:create')")
    public Result<Long> create(@Valid @RequestBody ProjectTypeDTO projectTypeDTO) {
        ProjectType projectType = projectTypeConvert.toEntity(projectTypeDTO);
        return Result.ok(projectTypeService.create(projectType).getId());
    }

    @PutMapping("/update")
    @Operation(summary = "修改项目类型")
    @Parameter(name = "projectTypeDTO", description = "项目类型信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:projectType:update')")
    public Result<Boolean> update(@Valid @RequestBody ProjectTypeDTO projectTypeDTO) {
        ProjectType projectType = projectTypeConvert.toEntity(projectTypeDTO);
        projectTypeService.update(projectType);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统项目类型")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:projectType:delete')")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        projectTypeService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @Operation(summary = "批量删除")
    @PreAuthorize("@ss.hasPermission('budget:projectType:batchDelete')")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @DeleteMapping(value = "/batchDelete")
    public Result<String> batchDelete(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        this.projectTypeService.batchDelete(ids);
        return Result.ok("批量删除成功!");
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取项目类型")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:projectType:query')")
    public Result<ProjectTypeDTO> get(@PathVariable("id") Long id) {
        ProjectType projectType = projectTypeService.get(id);
        return Result.ok(projectTypeConvert.toDTO(projectType));
    }

    @GetMapping("/list")
    @Operation(summary = "列表查询项目类型")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:projectType:query')")
    public Result<List<ProjectTypeDTO>> list(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        List<ProjectType> list = projectTypeService.list(ids);
        return Result.ok(projectTypeConvert.toDTOList(list));
    }

    /**
     * 分页查询
     * @paramprojectTypeDTO 查询对象
     * @param pageable 注传参格式： size=10&page=0 &sort=id,asc&sort=name,desc
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询项目类型")
    @Parameters({
            @Parameter(name = "projectTypeDTO", description = "查询对象", required = false),
            @Parameter(name = "pageable", description = "前页参数", required = true)
    })
    @PreAuthorize("@ss.hasPermission('budget:projectType:query')")
    public Result<PageResult<ProjectTypeDTO>> pageable(ProjectTypeDTO projectTypeDTO, @PageableDefault(size = 15) Pageable pageable) {
        ProjectType projectType = projectTypeConvert.toEntity(projectTypeDTO);
        PageResult<ProjectType> pageResult = projectTypeService.page(projectType, pageable);
        return Result.ok(projectTypeConvert.toPageResult(pageResult));
    }

    @GetMapping("/export")
    @Operation(summary = "导出项目类型Excel")
    @Parameter(name = "projectTypeDTO", description = "查询参数", required = true)
    @Parameter(name = "response", description = "HttpServletResponse", required = true)
    @PreAuthorize("@ss.hasPermission('budget:projectType:export')")
    public void export(ProjectTypeDTO projectTypeDTO,
                       HttpServletResponse response) throws IOException {
        ProjectType projectType = projectTypeConvert.toEntity(projectTypeDTO);
        List<ProjectType> list = projectTypeService.list(projectType);
        List<ProjectTypeDTO> result = projectTypeConvert.toDTOList(list);
        // 导出 Excel
        //List<ProjectTypeDTO> datas = ProjectTypeConvert.toDTOList(list);
        //ExcelUtils.write(response, "ProjectType.xls", "数据", ProjectTypeExcelDTO.class, datas);
    }
}
