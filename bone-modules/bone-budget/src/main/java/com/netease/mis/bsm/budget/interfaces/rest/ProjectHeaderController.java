package com.netease.mis.bsm.budget.interfaces.rest;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.netease.mis.bsm.budget.domain.model.ProjectHeader;
import com.netease.mis.bsm.budget.domain.service.ProjectHeaderService;
import com.netease.mis.bsm.budget.interfaces.convert.ProjectHeaderConvert;
import com.netease.mis.bsm.budget.interfaces.dto.ProjectHeaderDTO;
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
* 项目单头DTO
*
* @author 梅山源码
*/
@Tag(name = "系统 - 项目单头管理", description = "系统 - 项目单头管理")
@RestController
@RequestMapping("/budget/projectHeader")
@Validated
public class ProjectHeaderController {
    @Resource
    private ProjectHeaderService projectHeaderService;

    @Resource
    private ProjectHeaderConvert projectHeaderConvert;

    @PostMapping("/create")
    @Operation(summary = "创建项目单头")
    @Parameter(name = "projectHeaderDTO", description = "项目单头信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:projectHeader:create')")
    public Result<Long> create(@Valid @RequestBody ProjectHeaderDTO projectHeaderDTO) {
        ProjectHeader projectHeader = projectHeaderConvert.toEntity(projectHeaderDTO);
        return Result.ok(projectHeaderService.create(projectHeader).getId());
    }

    @PutMapping("/update")
    @Operation(summary = "修改项目单头")
    @Parameter(name = "projectHeaderDTO", description = "项目单头信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:projectHeader:update')")
    public Result<Boolean> update(@Valid @RequestBody ProjectHeaderDTO projectHeaderDTO) {
        ProjectHeader projectHeader = projectHeaderConvert.toEntity(projectHeaderDTO);
        projectHeaderService.update(projectHeader);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统项目单头")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:projectHeader:delete')")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        projectHeaderService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @Operation(summary = "批量删除")
    @PreAuthorize("@ss.hasPermission('budget:projectHeader:batchDelete')")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @DeleteMapping(value = "/batchDelete")
    public Result<String> batchDelete(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        this.projectHeaderService.batchDelete(ids);
        return Result.ok("批量删除成功!");
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取项目单头")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:projectHeader:query')")
    public Result<ProjectHeaderDTO> get(@PathVariable("id") Long id) {
        ProjectHeader projectHeader = projectHeaderService.get(id);
        return Result.ok(projectHeaderConvert.toDTO(projectHeader));
    }

    @GetMapping("/list")
    @Operation(summary = "列表查询项目单头")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:projectHeader:query')")
    public Result<List<ProjectHeaderDTO>> list(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        List<ProjectHeader> list = projectHeaderService.list(ids);
        return Result.ok(projectHeaderConvert.toDTOList(list));
    }

    /**
     * 分页查询
     * @paramprojectHeaderDTO 查询对象
     * @param pageable 注传参格式： size=10&page=0 &sort=id,asc&sort=name,desc
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询项目单头")
    @Parameters({
            @Parameter(name = "projectHeaderDTO", description = "查询对象", required = false),
            @Parameter(name = "pageable", description = "前页参数", required = true)
    })
    @PreAuthorize("@ss.hasPermission('budget:projectHeader:query')")
    public Result<PageResult<ProjectHeaderDTO>> pageable(ProjectHeaderDTO projectHeaderDTO, @PageableDefault(size = 15) Pageable pageable) {
        ProjectHeader projectHeader = projectHeaderConvert.toEntity(projectHeaderDTO);
        PageResult<ProjectHeader> pageResult = projectHeaderService.page(projectHeader, pageable);
        return Result.ok(projectHeaderConvert.toPageResult(pageResult));
    }

    @GetMapping("/export")
    @Operation(summary = "导出项目单头Excel")
    @Parameter(name = "projectHeaderDTO", description = "查询参数", required = true)
    @Parameter(name = "response", description = "HttpServletResponse", required = true)
    @PreAuthorize("@ss.hasPermission('budget:projectHeader:export')")
    public void export(ProjectHeaderDTO projectHeaderDTO,
                       HttpServletResponse response) throws IOException {
        ProjectHeader projectHeader = projectHeaderConvert.toEntity(projectHeaderDTO);
        List<ProjectHeader> list = projectHeaderService.list(projectHeader);
        List<ProjectHeaderDTO> result = projectHeaderConvert.toDTOList(list);
        // 导出 Excel
        //List<ProjectHeaderDTO> datas = ProjectHeaderConvert.toDTOList(list);
        //ExcelUtils.write(response, "ProjectHeader.xls", "数据", ProjectHeaderExcelDTO.class, datas);
    }
}
