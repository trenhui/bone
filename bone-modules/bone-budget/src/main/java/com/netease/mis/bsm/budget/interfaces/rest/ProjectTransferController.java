package com.netease.mis.bsm.budget.interfaces.rest;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.netease.mis.bsm.budget.domain.model.ProjectTransfer;
import com.netease.mis.bsm.budget.domain.service.ProjectTransferService;
import com.netease.mis.bsm.budget.interfaces.convert.ProjectTransferConvert;
import com.netease.mis.bsm.budget.interfaces.dto.ProjectTransferDTO;
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
* 预算MPC转移项目DTO
*
* @author 梅山源码
*/
@Tag(name = "系统 - 预算MPC转移项目管理", description = "系统 - 预算MPC转移项目管理")
@RestController
@RequestMapping("/budget/projectTransfer")
@Validated
public class ProjectTransferController {
    @Resource
    private ProjectTransferService projectTransferService;

    @Resource
    private ProjectTransferConvert projectTransferConvert;

    @PostMapping("/create")
    @Operation(summary = "创建预算MPC转移项目")
    @Parameter(name = "projectTransferDTO", description = "预算MPC转移项目信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:projectTransfer:create')")
    public Result<Long> create(@Valid @RequestBody ProjectTransferDTO projectTransferDTO) {
        ProjectTransfer projectTransfer = projectTransferConvert.toEntity(projectTransferDTO);
        return Result.ok(projectTransferService.create(projectTransfer).getId());
    }

    @PutMapping("/update")
    @Operation(summary = "修改预算MPC转移项目")
    @Parameter(name = "projectTransferDTO", description = "预算MPC转移项目信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:projectTransfer:update')")
    public Result<Boolean> update(@Valid @RequestBody ProjectTransferDTO projectTransferDTO) {
        ProjectTransfer projectTransfer = projectTransferConvert.toEntity(projectTransferDTO);
        projectTransferService.update(projectTransfer);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统预算MPC转移项目")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:projectTransfer:delete')")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        projectTransferService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @Operation(summary = "批量删除")
    @PreAuthorize("@ss.hasPermission('budget:projectTransfer:batchDelete')")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @DeleteMapping(value = "/batchDelete")
    public Result<String> batchDelete(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        this.projectTransferService.batchDelete(ids);
        return Result.ok("批量删除成功!");
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取预算MPC转移项目")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:projectTransfer:query')")
    public Result<ProjectTransferDTO> get(@PathVariable("id") Long id) {
        ProjectTransfer projectTransfer = projectTransferService.get(id);
        return Result.ok(projectTransferConvert.toDTO(projectTransfer));
    }

    @GetMapping("/list")
    @Operation(summary = "列表查询预算MPC转移项目")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:projectTransfer:query')")
    public Result<List<ProjectTransferDTO>> list(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        List<ProjectTransfer> list = projectTransferService.list(ids);
        return Result.ok(projectTransferConvert.toDTOList(list));
    }

    /**
     * 分页查询
     * @paramprojectTransferDTO 查询对象
     * @param pageable 注传参格式： size=10&page=0 &sort=id,asc&sort=name,desc
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询预算MPC转移项目")
    @Parameters({
            @Parameter(name = "projectTransferDTO", description = "查询对象", required = false),
            @Parameter(name = "pageable", description = "前页参数", required = true)
    })
    @PreAuthorize("@ss.hasPermission('budget:projectTransfer:query')")
    public Result<PageResult<ProjectTransferDTO>> pageable(ProjectTransferDTO projectTransferDTO, @PageableDefault(size = 15) Pageable pageable) {
        ProjectTransfer projectTransfer = projectTransferConvert.toEntity(projectTransferDTO);
        PageResult<ProjectTransfer> pageResult = projectTransferService.page(projectTransfer, pageable);
        return Result.ok(projectTransferConvert.toPageResult(pageResult));
    }

    @GetMapping("/export")
    @Operation(summary = "导出预算MPC转移项目Excel")
    @Parameter(name = "projectTransferDTO", description = "查询参数", required = true)
    @Parameter(name = "response", description = "HttpServletResponse", required = true)
    @PreAuthorize("@ss.hasPermission('budget:projectTransfer:export')")
    public void export(ProjectTransferDTO projectTransferDTO,
                       HttpServletResponse response) throws IOException {
        ProjectTransfer projectTransfer = projectTransferConvert.toEntity(projectTransferDTO);
        List<ProjectTransfer> list = projectTransferService.list(projectTransfer);
        List<ProjectTransferDTO> result = projectTransferConvert.toDTOList(list);
        // 导出 Excel
        //List<ProjectTransferDTO> datas = ProjectTransferConvert.toDTOList(list);
        //ExcelUtils.write(response, "ProjectTransfer.xls", "数据", ProjectTransferExcelDTO.class, datas);
    }
}
