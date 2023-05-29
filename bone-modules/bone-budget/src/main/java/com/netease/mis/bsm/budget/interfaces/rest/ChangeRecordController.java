package com.netease.mis.bsm.budget.interfaces.rest;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.netease.mis.bsm.budget.domain.model.ChangeRecord;
import com.netease.mis.bsm.budget.domain.service.ChangeRecordService;
import com.netease.mis.bsm.budget.interfaces.convert.ChangeRecordConvert;
import com.netease.mis.bsm.budget.interfaces.dto.ChangeRecordDTO;
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
* 预算变动记录DTO
*
* @author 梅山源码
*/
@Tag(name = "系统 - 预算变动记录管理", description = "系统 - 预算变动记录管理")
@RestController
@RequestMapping("/budget/changeRecord")
@Validated
public class ChangeRecordController {
    @Resource
    private ChangeRecordService changeRecordService;

    @Resource
    private ChangeRecordConvert changeRecordConvert;

    @PostMapping("/create")
    @Operation(summary = "创建预算变动记录")
    @Parameter(name = "changeRecordDTO", description = "预算变动记录信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:changeRecord:create')")
    public Result<Long> create(@Valid @RequestBody ChangeRecordDTO changeRecordDTO) {
        ChangeRecord changeRecord = changeRecordConvert.toEntity(changeRecordDTO);
        return Result.ok(changeRecordService.create(changeRecord).getId());
    }

    @PutMapping("/update")
    @Operation(summary = "修改预算变动记录")
    @Parameter(name = "changeRecordDTO", description = "预算变动记录信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:changeRecord:update')")
    public Result<Boolean> update(@Valid @RequestBody ChangeRecordDTO changeRecordDTO) {
        ChangeRecord changeRecord = changeRecordConvert.toEntity(changeRecordDTO);
        changeRecordService.update(changeRecord);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统预算变动记录")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:changeRecord:delete')")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        changeRecordService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @Operation(summary = "批量删除")
    @PreAuthorize("@ss.hasPermission('budget:changeRecord:batchDelete')")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @DeleteMapping(value = "/batchDelete")
    public Result<String> batchDelete(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        this.changeRecordService.batchDelete(ids);
        return Result.ok("批量删除成功!");
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取预算变动记录")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:changeRecord:query')")
    public Result<ChangeRecordDTO> get(@PathVariable("id") Long id) {
        ChangeRecord changeRecord = changeRecordService.get(id);
        return Result.ok(changeRecordConvert.toDTO(changeRecord));
    }

    @GetMapping("/list")
    @Operation(summary = "列表查询预算变动记录")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:changeRecord:query')")
    public Result<List<ChangeRecordDTO>> list(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        List<ChangeRecord> list = changeRecordService.list(ids);
        return Result.ok(changeRecordConvert.toDTOList(list));
    }

    /**
     * 分页查询
     * @paramchangeRecordDTO 查询对象
     * @param pageable 注传参格式： size=10&page=0 &sort=id,asc&sort=name,desc
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询预算变动记录")
    @Parameters({
            @Parameter(name = "changeRecordDTO", description = "查询对象", required = false),
            @Parameter(name = "pageable", description = "前页参数", required = true)
    })
    @PreAuthorize("@ss.hasPermission('budget:changeRecord:query')")
    public Result<PageResult<ChangeRecordDTO>> pageable(ChangeRecordDTO changeRecordDTO, @PageableDefault(size = 15) Pageable pageable) {
        ChangeRecord changeRecord = changeRecordConvert.toEntity(changeRecordDTO);
        PageResult<ChangeRecord> pageResult = changeRecordService.page(changeRecord, pageable);
        return Result.ok(changeRecordConvert.toPageResult(pageResult));
    }

    @GetMapping("/export")
    @Operation(summary = "导出预算变动记录Excel")
    @Parameter(name = "changeRecordDTO", description = "查询参数", required = true)
    @Parameter(name = "response", description = "HttpServletResponse", required = true)
    @PreAuthorize("@ss.hasPermission('budget:changeRecord:export')")
    public void export(ChangeRecordDTO changeRecordDTO,
                       HttpServletResponse response) throws IOException {
        ChangeRecord changeRecord = changeRecordConvert.toEntity(changeRecordDTO);
        List<ChangeRecord> list = changeRecordService.list(changeRecord);
        List<ChangeRecordDTO> result = changeRecordConvert.toDTOList(list);
        // 导出 Excel
        //List<ChangeRecordDTO> datas = ChangeRecordConvert.toDTOList(list);
        //ExcelUtils.write(response, "ChangeRecord.xls", "数据", ChangeRecordExcelDTO.class, datas);
    }
}
