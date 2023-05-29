package com.bone.lowcode.studio.interfaces.rest;

import com.bone.core.result.PageResult;
import com.bone.core.result.Result;
import com.bone.lowcode.studio.domain.model.Form;
import com.bone.lowcode.studio.domain.service.FormService;
import com.bone.lowcode.studio.interfaces.convert.FormConvert;
import com.bone.lowcode.studio.interfaces.dto.FormDTO;
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
* 表单DTO
*
* @author 梅山源码
*/
@Tag(name = "系统 - 表单管理", description = "系统 - 表单管理")
@RestController
@RequestMapping("/studio/form")
@Validated
public class FormController {
    @Resource
    private FormService formService;

    @Resource
    private FormConvert formConvert;

    @PostMapping("/create")
    @Operation(summary = "创建表单")
    @Parameter(name = "formDTO", description = "表单信息", required = true)
    @PreAuthorize("@ss.hasPermission('studio:form:create')")
    public Result<Long> create(@Valid @RequestBody FormDTO formDTO) {
        Form form = formConvert.toEntity(formDTO);
        return Result.ok(formService.create(form).getId());
    }

    @PutMapping("/update")
    @Operation(summary = "修改表单")
    @Parameter(name = "formDTO", description = "表单信息", required = true)
    @PreAuthorize("@ss.hasPermission('studio:form:update')")
    public Result<Boolean> update(@Valid @RequestBody FormDTO formDTO) {
        Form form = formConvert.toEntity(formDTO);
        formService.update(form);
        return Result.ok();
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除系统表单")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('studio:form:delete')")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        formService.delete(id);
        return Result.ok();
    }

    /**
     * 批量删除
     *
     * @param ids
     * @return
     */
    @Operation(summary = "批量删除")
    @PreAuthorize("@ss.hasPermission('studio:form:batchDelete')")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @DeleteMapping(value = "/batchDelete")
    public Result<String> batchDelete(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        this.formService.batchDelete(ids);
        return Result.ok("批量删除成功!");
    }

    @GetMapping("/get/{id}")
    @Operation(summary = "获取表单")
    @Parameter(name = "id", description = "主键id", required = true, in = ParameterIn.PATH, style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('studio:form:query')")
    public Result<FormDTO> get(@PathVariable("id") Long id) {
        Form form = formService.get(id);
        return Result.ok(formConvert.toDTO(form));
    }

    @GetMapping("/list")
    @Operation(summary = "列表查询表单")
    @Parameter(name = "ids", description = "主键id列表", required = true, in = ParameterIn.QUERY, example = "1024,2048", style = ParameterStyle.SIMPLE)
    @PreAuthorize("@ss.hasPermission('studio:form:query')")
    public Result<List<FormDTO>> list(@RequestParam(name = "ids", required = true) Collection<Long> ids) {
        List<Form> list = formService.list(ids);
        return Result.ok(formConvert.toDTOList(list));
    }

    /**
     * 分页查询
     * @paramformDTO 查询对象
     * @param pageable 注传参格式： size=10&page=0 &sort=id,asc&sort=name,desc
     * @return
     */
    @GetMapping("/page")
    @Operation(summary = "分页查询表单")
    @Parameters({
            @Parameter(name = "formDTO", description = "查询对象", required = false),
            @Parameter(name = "pageable", description = "前页参数", required = true)
    })
    @PreAuthorize("@ss.hasPermission('studio:form:query')")
    public Result<PageResult<FormDTO>> pageable(FormDTO formDTO, @PageableDefault(size = 15) Pageable pageable) {
        Form form = formConvert.toEntity(formDTO);
        PageResult<Form> pageResult = formService.page(form, pageable);
        return Result.ok(formConvert.toPageResult(pageResult));
    }

    @GetMapping("/export")
    @Operation(summary = "导出表单Excel")
    @Parameter(name = "formDTO", description = "查询参数", required = true)
    @Parameter(name = "response", description = "HttpServletResponse", required = true)
    @PreAuthorize("@ss.hasPermission('studio:form:export')")
    public void export(FormDTO formDTO,
                       HttpServletResponse response) throws IOException {
        Form form = formConvert.toEntity(formDTO);
        List<Form> list = formService.list(form);
        List<FormDTO> result = formConvert.toDTOList(list);
        // 导出 Excel
        //List<FormDTO> datas = FormConvert.toDTOList(list);
        //ExcelUtils.write(response, "Form.xls", "数据", FormExcelDTO.class, datas);
    }
}
