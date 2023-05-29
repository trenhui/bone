package com.netease.mis.bsm.budget.interfaces.rest;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.netease.mis.bsm.budget.domain.model.Attachment;
import com.netease.mis.bsm.budget.domain.service.AttachmentService;
import com.netease.mis.bsm.budget.interfaces.convert.AttachmentConvert;
import com.netease.mis.bsm.budget.interfaces.dto.AttachmentDTO;
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
* 单据附件DTO
*
* @author 梅山源码
*/
@Tag(name = "系统 - 单据附件管理", description = "系统 - 单据附件管理")
@RestController
@RequestMapping("/budget/attachment")
@Validated
public class AttachmentController {
    @Resource
    private AttachmentService attachmentService;

    @Resource
    private AttachmentConvert attachmentConvert;

    @PostMapping("/create")
    @Operation(summary = "创建单据附件")
    @Parameter(name = "attachmentDTO", description = "单据附件信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:attachment:create')")
    public Result<Long> create(@Valid @RequestBody AttachmentDTO attachmentDTO) {
        Attachment attachment = attachmentConvert.toEntity(attachmentDTO);
        return Result.ok(attachmentService.create(attachment).getId());
    }

    @PutMapping("/update")
    @Operation(summary = "修改单据附件")
    @Parameter(name = "attachmentDTO", description = "单据附件信息", required = true)
    @PreAuthorize("@ss.hasPermission('budget:attachment:update')")
    public Result<Boolean> update(@Valid @RequestBody AttachmentDTO attachmentDTO) {
        Attachment attachment = attachmentConvert.toEntity(attachmentDTO);
        attachmentService.update(attachment);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统单据附件")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:attachment:delete')")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        attachmentService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @Operation(summary = "批量删除")
    @PreAuthorize("@ss.hasPermission('budget:attachment:batchDelete')")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @DeleteMapping(value = "/batchDelete")
    public Result<String> batchDelete(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        this.attachmentService.batchDelete(ids);
        return Result.ok("批量删除成功!");
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取单据附件")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:attachment:query')")
    public Result<AttachmentDTO> get(@PathVariable("id") Long id) {
        Attachment attachment = attachmentService.get(id);
        return Result.ok(attachmentConvert.toDTO(attachment));
    }

    @GetMapping("/list")
    @Operation(summary = "列表查询单据附件")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('budget:attachment:query')")
    public Result<List<AttachmentDTO>> list(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        List<Attachment> list = attachmentService.list(ids);
        return Result.ok(attachmentConvert.toDTOList(list));
    }

    /**
     * 分页查询
     * @paramattachmentDTO 查询对象
     * @param pageable 注传参格式： size=10&page=0 &sort=id,asc&sort=name,desc
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询单据附件")
    @Parameters({
            @Parameter(name = "attachmentDTO", description = "查询对象", required = false),
            @Parameter(name = "pageable", description = "前页参数", required = true)
    })
    @PreAuthorize("@ss.hasPermission('budget:attachment:query')")
    public Result<PageResult<AttachmentDTO>> pageable(AttachmentDTO attachmentDTO, @PageableDefault(size = 15) Pageable pageable) {
        Attachment attachment = attachmentConvert.toEntity(attachmentDTO);
        PageResult<Attachment> pageResult = attachmentService.page(attachment, pageable);
        return Result.ok(attachmentConvert.toPageResult(pageResult));
    }

    @GetMapping("/export")
    @Operation(summary = "导出单据附件Excel")
    @Parameter(name = "attachmentDTO", description = "查询参数", required = true)
    @Parameter(name = "response", description = "HttpServletResponse", required = true)
    @PreAuthorize("@ss.hasPermission('budget:attachment:export')")
    public void export(AttachmentDTO attachmentDTO,
                       HttpServletResponse response) throws IOException {
        Attachment attachment = attachmentConvert.toEntity(attachmentDTO);
        List<Attachment> list = attachmentService.list(attachment);
        List<AttachmentDTO> result = attachmentConvert.toDTOList(list);
        // 导出 Excel
        //List<AttachmentDTO> datas = AttachmentConvert.toDTOList(list);
        //ExcelUtils.write(response, "Attachment.xls", "数据", AttachmentExcelDTO.class, datas);
    }
}
