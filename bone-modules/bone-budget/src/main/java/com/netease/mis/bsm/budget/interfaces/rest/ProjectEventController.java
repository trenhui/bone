package com.netease.mis.bsm.budget.interfaces.rest;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.netease.mis.bsm.budget.domain.model.ProjectEvent;
import com.netease.mis.bsm.budget.domain.service.ProjectEventService;
import com.netease.mis.bsm.budget.interfaces.convert.ProjectEventConvert;
import com.netease.mis.bsm.budget.interfaces.dto.ProjectEventDTO;
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
* 事件DTO
*
* @author 梅山源码
*/
@Tag(name = "系统 - 事件管理", description = "系统 - 事件管理")
@RestController
@RequestMapping("/budget/projectEvent")
@Validated
public class ProjectEventController {
    @Resource
    private ProjectEventService projectEventService;

    @Resource
    private ProjectEventConvert projectEventConvert;

    @PostMapping("/create")
    @Operation(summary = "创建事件")
    @Parameter(name = "projectEventDTO", description = "事件信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:projectEvent:create')")
    public Result<Long> create(@Valid @RequestBody ProjectEventDTO projectEventDTO) {
        ProjectEvent projectEvent = projectEventConvert.toEntity(projectEventDTO);
        return Result.ok(projectEventService.create(projectEvent).getId());
    }

    @PutMapping("/update")
    @Operation(summary = "修改事件")
    @Parameter(name = "projectEventDTO", description = "事件信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:projectEvent:update')")
    public Result<Boolean> update(@Valid @RequestBody ProjectEventDTO projectEventDTO) {
        ProjectEvent projectEvent = projectEventConvert.toEntity(projectEventDTO);
        projectEventService.update(projectEvent);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统事件")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:projectEvent:delete')")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        projectEventService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @Operation(summary = "批量删除")
    @PreAuthorize("@ss.hasPermission('budget:projectEvent:batchDelete')")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @DeleteMapping(value = "/batchDelete")
    public Result<String> batchDelete(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        this.projectEventService.batchDelete(ids);
        return Result.ok("批量删除成功!");
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取事件")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:projectEvent:query')")
    public Result<ProjectEventDTO> get(@PathVariable("id") Long id) {
        ProjectEvent projectEvent = projectEventService.get(id);
        return Result.ok(projectEventConvert.toDTO(projectEvent));
    }

    @GetMapping("/list")
    @Operation(summary = "列表查询事件")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:projectEvent:query')")
    public Result<List<ProjectEventDTO>> list(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        List<ProjectEvent> list = projectEventService.list(ids);
        return Result.ok(projectEventConvert.toDTOList(list));
    }

    /**
     * 分页查询
     * @paramprojectEventDTO 查询对象
     * @param pageable 注传参格式： size=10&page=0 &sort=id,asc&sort=name,desc
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询事件")
    @Parameters({
            @Parameter(name = "projectEventDTO", description = "查询对象", required = false),
            @Parameter(name = "pageable", description = "前页参数", required = true)
    })
    @PreAuthorize("@ss.hasPermission('budget:projectEvent:query')")
    public Result<PageResult<ProjectEventDTO>> pageable(ProjectEventDTO projectEventDTO, @PageableDefault(size = 15) Pageable pageable) {
        ProjectEvent projectEvent = projectEventConvert.toEntity(projectEventDTO);
        PageResult<ProjectEvent> pageResult = projectEventService.page(projectEvent, pageable);
        return Result.ok(projectEventConvert.toPageResult(pageResult));
    }

    @GetMapping("/export")
    @Operation(summary = "导出事件Excel")
    @Parameter(name = "projectEventDTO", description = "查询参数", required = true)
    @Parameter(name = "response", description = "HttpServletResponse", required = true)
    @PreAuthorize("@ss.hasPermission('budget:projectEvent:export')")
    public void export(ProjectEventDTO projectEventDTO,
                       HttpServletResponse response) throws IOException {
        ProjectEvent projectEvent = projectEventConvert.toEntity(projectEventDTO);
        List<ProjectEvent> list = projectEventService.list(projectEvent);
        List<ProjectEventDTO> result = projectEventConvert.toDTOList(list);
        // 导出 Excel
        //List<ProjectEventDTO> datas = ProjectEventConvert.toDTOList(list);
        //ExcelUtils.write(response, "ProjectEvent.xls", "数据", ProjectEventExcelDTO.class, datas);
    }
}
