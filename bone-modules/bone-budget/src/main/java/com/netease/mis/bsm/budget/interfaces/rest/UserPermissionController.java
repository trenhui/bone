package com.netease.mis.bsm.budget.interfaces.rest;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.netease.mis.bsm.budget.domain.model.UserPermission;
import com.netease.mis.bsm.budget.domain.service.UserPermissionService;
import com.netease.mis.bsm.budget.interfaces.convert.UserPermissionConvert;
import com.netease.mis.bsm.budget.interfaces.dto.UserPermissionDTO;
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
* 预算用户权限DTO
*
* @author 梅山源码
*/
@Tag(name = "系统 - 预算用户权限管理", description = "系统 - 预算用户权限管理")
@RestController
@RequestMapping("/budget/userPermission")
@Validated
public class UserPermissionController {
    @Resource
    private UserPermissionService userPermissionService;

    @Resource
    private UserPermissionConvert userPermissionConvert;

    @PostMapping("/create")
    @Operation(summary = "创建预算用户权限")
    @Parameter(name = "userPermissionDTO", description = "预算用户权限信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:userPermission:create')")
    public Result<Long> create(@Valid @RequestBody UserPermissionDTO userPermissionDTO) {
        UserPermission userPermission = userPermissionConvert.toEntity(userPermissionDTO);
        return Result.ok(userPermissionService.create(userPermission).getId());
    }

    @PutMapping("/update")
    @Operation(summary = "修改预算用户权限")
    @Parameter(name = "userPermissionDTO", description = "预算用户权限信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:userPermission:update')")
    public Result<Boolean> update(@Valid @RequestBody UserPermissionDTO userPermissionDTO) {
        UserPermission userPermission = userPermissionConvert.toEntity(userPermissionDTO);
        userPermissionService.update(userPermission);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统预算用户权限")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:userPermission:delete')")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        userPermissionService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @Operation(summary = "批量删除")
    @PreAuthorize("@ss.hasPermission('budget:userPermission:batchDelete')")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @DeleteMapping(value = "/batchDelete")
    public Result<String> batchDelete(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        this.userPermissionService.batchDelete(ids);
        return Result.ok("批量删除成功!");
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取预算用户权限")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:userPermission:query')")
    public Result<UserPermissionDTO> get(@PathVariable("id") Long id) {
        UserPermission userPermission = userPermissionService.get(id);
        return Result.ok(userPermissionConvert.toDTO(userPermission));
    }

    @GetMapping("/list")
    @Operation(summary = "列表查询预算用户权限")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:userPermission:query')")
    public Result<List<UserPermissionDTO>> list(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        List<UserPermission> list = userPermissionService.list(ids);
        return Result.ok(userPermissionConvert.toDTOList(list));
    }

    /**
     * 分页查询
     * @paramuserPermissionDTO 查询对象
     * @param pageable 注传参格式： size=10&page=0 &sort=id,asc&sort=name,desc
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询预算用户权限")
    @Parameters({
            @Parameter(name = "userPermissionDTO", description = "查询对象", required = false),
            @Parameter(name = "pageable", description = "前页参数", required = true)
    })
    @PreAuthorize("@ss.hasPermission('budget:userPermission:query')")
    public Result<PageResult<UserPermissionDTO>> pageable(UserPermissionDTO userPermissionDTO, @PageableDefault(size = 15) Pageable pageable) {
        UserPermission userPermission = userPermissionConvert.toEntity(userPermissionDTO);
        PageResult<UserPermission> pageResult = userPermissionService.page(userPermission, pageable);
        return Result.ok(userPermissionConvert.toPageResult(pageResult));
    }

    @GetMapping("/export")
    @Operation(summary = "导出预算用户权限Excel")
    @Parameter(name = "userPermissionDTO", description = "查询参数", required = true)
    @Parameter(name = "response", description = "HttpServletResponse", required = true)
    @PreAuthorize("@ss.hasPermission('budget:userPermission:export')")
    public void export(UserPermissionDTO userPermissionDTO,
                       HttpServletResponse response) throws IOException {
        UserPermission userPermission = userPermissionConvert.toEntity(userPermissionDTO);
        List<UserPermission> list = userPermissionService.list(userPermission);
        List<UserPermissionDTO> result = userPermissionConvert.toDTOList(list);
        // 导出 Excel
        //List<UserPermissionDTO> datas = UserPermissionConvert.toDTOList(list);
        //ExcelUtils.write(response, "UserPermission.xls", "数据", UserPermissionExcelDTO.class, datas);
    }
}
