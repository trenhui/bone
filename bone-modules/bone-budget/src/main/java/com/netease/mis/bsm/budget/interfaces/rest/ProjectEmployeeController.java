package com.netease.mis.bsm.budget.interfaces.rest;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.netease.mis.bsm.budget.domain.model.ProjectEmployee;
import com.netease.mis.bsm.budget.domain.service.ProjectEmployeeService;
import com.netease.mis.bsm.budget.interfaces.convert.ProjectEmployeeConvert;
import com.netease.mis.bsm.budget.interfaces.dto.ProjectEmployeeDTO;
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
* 项目成员DTO
*
* @author 梅山源码
*/
@Tag(name = "系统 - 项目成员管理", description = "系统 - 项目成员管理")
@RestController
@RequestMapping("/budget/projectEmployee")
@Validated
public class ProjectEmployeeController {
    @Resource
    private ProjectEmployeeService projectEmployeeService;

    @Resource
    private ProjectEmployeeConvert projectEmployeeConvert;

    @PostMapping("/create")
    @Operation(summary = "创建项目成员")
    @Parameter(name = "projectEmployeeDTO", description = "项目成员信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:projectEmployee:create')")
    public Result<Long> create(@Valid @RequestBody ProjectEmployeeDTO projectEmployeeDTO) {
        ProjectEmployee projectEmployee = projectEmployeeConvert.toEntity(projectEmployeeDTO);
        return Result.ok(projectEmployeeService.create(projectEmployee).getId());
    }

    @PutMapping("/update")
    @Operation(summary = "修改项目成员")
    @Parameter(name = "projectEmployeeDTO", description = "项目成员信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:projectEmployee:update')")
    public Result<Boolean> update(@Valid @RequestBody ProjectEmployeeDTO projectEmployeeDTO) {
        ProjectEmployee projectEmployee = projectEmployeeConvert.toEntity(projectEmployeeDTO);
        projectEmployeeService.update(projectEmployee);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统项目成员")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:projectEmployee:delete')")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        projectEmployeeService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @Operation(summary = "批量删除")
    @PreAuthorize("@ss.hasPermission('budget:projectEmployee:batchDelete')")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @DeleteMapping(value = "/batchDelete")
    public Result<String> batchDelete(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        this.projectEmployeeService.batchDelete(ids);
        return Result.ok("批量删除成功!");
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取项目成员")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:projectEmployee:query')")
    public Result<ProjectEmployeeDTO> get(@PathVariable("id") Long id) {
        ProjectEmployee projectEmployee = projectEmployeeService.get(id);
        return Result.ok(projectEmployeeConvert.toDTO(projectEmployee));
    }

    @GetMapping("/list")
    @Operation(summary = "列表查询项目成员")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:projectEmployee:query')")
    public Result<List<ProjectEmployeeDTO>> list(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        List<ProjectEmployee> list = projectEmployeeService.list(ids);
        return Result.ok(projectEmployeeConvert.toDTOList(list));
    }

    /**
     * 分页查询
     * @paramprojectEmployeeDTO 查询对象
     * @param pageable 注传参格式： size=10&page=0 &sort=id,asc&sort=name,desc
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询项目成员")
    @Parameters({
            @Parameter(name = "projectEmployeeDTO", description = "查询对象", required = false),
            @Parameter(name = "pageable", description = "前页参数", required = true)
    })
    @PreAuthorize("@ss.hasPermission('budget:projectEmployee:query')")
    public Result<PageResult<ProjectEmployeeDTO>> pageable(ProjectEmployeeDTO projectEmployeeDTO, @PageableDefault(size = 15) Pageable pageable) {
        ProjectEmployee projectEmployee = projectEmployeeConvert.toEntity(projectEmployeeDTO);
        PageResult<ProjectEmployee> pageResult = projectEmployeeService.page(projectEmployee, pageable);
        return Result.ok(projectEmployeeConvert.toPageResult(pageResult));
    }

    @GetMapping("/export")
    @Operation(summary = "导出项目成员Excel")
    @Parameter(name = "projectEmployeeDTO", description = "查询参数", required = true)
    @Parameter(name = "response", description = "HttpServletResponse", required = true)
    @PreAuthorize("@ss.hasPermission('budget:projectEmployee:export')")
    public void export(ProjectEmployeeDTO projectEmployeeDTO,
                       HttpServletResponse response) throws IOException {
        ProjectEmployee projectEmployee = projectEmployeeConvert.toEntity(projectEmployeeDTO);
        List<ProjectEmployee> list = projectEmployeeService.list(projectEmployee);
        List<ProjectEmployeeDTO> result = projectEmployeeConvert.toDTOList(list);
        // 导出 Excel
        //List<ProjectEmployeeDTO> datas = ProjectEmployeeConvert.toDTOList(list);
        //ExcelUtils.write(response, "ProjectEmployee.xls", "数据", ProjectEmployeeExcelDTO.class, datas);
    }
}
