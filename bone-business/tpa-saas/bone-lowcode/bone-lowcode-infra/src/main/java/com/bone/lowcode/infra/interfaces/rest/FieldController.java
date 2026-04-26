package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.dto.field.*;
import com.bone.lowcode.infra.application.dto.optionSet.SetFieldDataSourceDTO;
import com.bone.lowcode.infra.application.service.FieldApplicationService;
import com.bone.lowcode.infra.application.vo.field.*;
import com.bone.lowcode.infra.application.vo.optionSet.FieldDataSourceVO;
import com.bone.lowcode.infra.application.vo.table.FieldSimpleInfo;
import com.bone.lowcode.infra.infrastructure.common.annotation.HandleRepeatedReq;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/field")
@Validated
public class FieldController {

    @Autowired
    private FieldApplicationService fieldApplicationService;


    /**
     * 修改业务字段的属性
     */
    @PostMapping("/updateById")
    public Result<String> updateField(@RequestBody UpdateFieldDTO updateFieldDTO) throws ParseException {
        boolean flag = fieldApplicationService.updateField(updateFieldDTO);
        if (flag) return Result.ok();
        else return Result.error("更新失败！");
    }

    /**
     * 查询某fieldSet下以区块字段形式展示的所有字段的默认显示、必填属性
     */
    @GetMapping("/listDefaultByFieldSetId")
    public Result<List<FieldDefaultByBlockIdVO>> listDefaultByFieldSetId(@RequestParam("fieldSetId") Long fieldSetId) {
        List<FieldDefaultByBlockIdVO> voList = fieldApplicationService.listDefaultByFieldSetId(fieldSetId);
        return Result.ok(voList);
    }

    /**
     * 查询某model下的所有字段的默认显示、必填属性
     */
    @GetMapping("/listDefaultByModelId")
    public Result<List<FieldDefaultByModelIdVO>> getFieldDefaultByModelId(@RequestParam("modelId") Long modelId) {
        List<FieldDefaultByModelIdVO> voList = fieldApplicationService.getFieldDefaultByModelId(modelId);
        return Result.ok(voList);
    }

    /**
     * 批量更新业务字段的显示、必填属性、dataBinding、componentType
     */
    @PostMapping("/batchUpdateDefault")
    public Result<String> batchUpdateDefault(@RequestBody List<BatchUpdateFieldDefaultDTO> modifyList) {
        boolean flag = fieldApplicationService.batchUpdateDefault(modifyList);
        if (flag) return Result.ok();
        else return Result.error("保存失败！");
    }

    /**
     * 查询某fieldSet下的所有字段的行、列、宽度
     */
    @GetMapping("/listLocationByFieldSetId")
    public Result<List<FieldLocationVO>> listLocationByFieldSetId(@RequestParam("fieldSetId") Long fieldSetId) {
        List<FieldLocationVO> voList = fieldApplicationService.listLocationByFieldSetId(fieldSetId);
        return Result.ok(voList);
    }

    /**
     * 批量更新业务字段的行、列、宽度
     */
    @PostMapping("/batchUpdateLocation")
    public Result<String> batchUpdateLocation(@RequestBody List<BatchUpdateFieldLocationDTO> modifyList) {
        boolean flag = fieldApplicationService.batchUpdateLocation(modifyList);
        if (flag) return Result.ok();
        else return Result.error("保存失败！");
    }

    /**
     * 创建专属字段
     */
    @HandleRepeatedReq(true)
    @PostMapping("/createExclusiveField")
    public Result<String> createExclusiveField(@Validated @RequestBody CreateExclusiveFieldDTO dto) {
        boolean flag = fieldApplicationService.createExclusiveField(dto);
        if (flag) return Result.ok("创建专属字段成功!");
        else return Result.error("创建专属字段失败!");
    }

    /**
     * 根据id查询字段
     */
    @GetMapping("/getById")
    public Result getById(@RequestParam("id") Long id) {
        GetByIdVo vo = fieldApplicationService.getById(id);
        return Result.ok(vo);
    }

    /**
     * 获取当前页面、同组件类型的其它字段列表(不包含MainBlock,不包含表格)
     */
    @GetMapping("/getSameTypeFieldList")
    public Result getSameTypeFieldList(@RequestParam("fieldId") Long fieldId) {
        List<GetSameTypeFieldListVO> voList = fieldApplicationService.getSameTypeFieldList(fieldId);
        return Result.ok(voList);
    }

    /**
     * 获取当前页面的字段列表(不包含MainBlock,不包含表格)
     */
//    @GetMapping("/getByPageCode")
    public Result getByPageCode(@RequestParam("pageCode") String pageCode,
                                @RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        List<FieldVO> voList = fieldApplicationService.getByPageCode(pageCode, bizIdentityCode);
        return Result.ok(voList);
    }

    /**
     * 获取指定表格中的字段列表
     */
//    @GetMapping("/getFieldByTableId")
    public Result getFieldByTableId(@RequestParam("tableId") Long tableId) {
        List<FieldVO> voList = fieldApplicationService.getFieldByTableId(tableId);
        return Result.ok(voList);
    }

    /**
     * 获取当前页面的其它字段列表(不包含MainBlock,不包含表格)
     */
    @GetMapping("/getSamePageFieldList")
    public Result getSamePageFieldList(@RequestParam("fieldId") Long fieldId) {
        List<GetSameTypeFieldListVO> voList = fieldApplicationService.getSamePageFieldList(fieldId);
        return Result.ok(voList);
    }

    /**
     * 获取指定页面、指定组件类型且展示的字段
     */
    @GetMapping("/getByPageAndComponentType")
    public Result getByPageAndComponentType(@RequestParam("componentTypeList") List<String> componentTypeList,
                                            @RequestParam("pageCode") String pageCode,
                                            @RequestParam("bizIdentityCode") String bizIdentityCode) {
        List<GetByPageAndComponentTypeVO> voList = fieldApplicationService.getByPageAndComponentType(componentTypeList, pageCode, bizIdentityCode);
        return Result.ok(voList);
    }

    /**
     * 获取指定字段所在页面、指定组件类型的字段(不包含表格)
     */
    @GetMapping("/getByFieldIdAndComponentType")
    public Result getByFieldAndComponentType(@RequestParam("fieldId") Long fieldId,
                                             @RequestParam("componentType") String componentType) {
        List<String> compentTypeList = Arrays.stream(componentType.split(",")).toList();
        List<FieldSimpleInfo> voList = fieldApplicationService.getByFieldAndComponentType(fieldId, compentTypeList);
        return Result.ok(voList);
    }

    /**
     * 获取指定字段所在模型的、指定组件类型的字段
     */
    @GetMapping("/getByTableFieldIdAndComponentType")
    public Result getByTableFieldIdAndComponentType(@RequestParam("fieldId") Long fieldId,
                                                    @RequestParam("componentType") String componentType) {
        List<String> compentTypeList = Arrays.stream(componentType.split(",")).toList();
        List<FieldSimpleInfo> voList = fieldApplicationService.getByTableFieldIdAndComponentType(fieldId, compentTypeList);
        return Result.ok(voList);
    }

    /**
     * 查询某model下所有字段的基础信息，用于流程页面字段配置
     */
    @GetMapping("/getByModelId")
    public Result<GetByModelIdVO> getByModelId(@RequestParam("modelId") Long modelId) {
        GetByModelIdVO vo = fieldApplicationService.getByModelId(modelId);
        return Result.ok(vo);
    }

    /**
     * 查询某table下各model的字段的基础信息，用于流程页面字段配置
     */
    @GetMapping("/getByTableId")
    public Result getByTableId(@RequestParam("tableId") Long tableId) {
        List<GetByModelIdVO> voList = fieldApplicationService.getByTableId(tableId);
        return Result.ok(voList);
    }

    /**
     * 查询主体的下拉框字段绑定的数据源
     */
    @GetMapping("/getFieldDataSource")
    public Result getFieldDataSource(@RequestParam("bizIdentityCode") String bizIdentityCode) {
        List<FieldDataSourceVO> voList = fieldApplicationService.getFieldDataSource(bizIdentityCode);
        return Result.ok(voList);
    }

    /**
     * 设置下拉框字段的数据源
     */
    @PostMapping("/setFieldDataSource")
    public Result setFieldDataSource(@Validated @RequestBody SetFieldDataSourceDTO param) {
        boolean flag = fieldApplicationService.setFieldDataSource(param);
        if (flag) return Result.ok("设置完成!");
        else return Result.error("设置失败!");
    }

    /**
     * 主体专属配置页下，专属字段列表
     *
     * @param bizIdentityCode
     * @return
     */
    @GetMapping("/listExclusiveField")
    public Result<List<FieldDefaultByModelIdVO>> listExclusiveField(@RequestParam("bizIdentityCode") String bizIdentityCode) {
        return fieldApplicationService.listByBizIdentityCode(bizIdentityCode);
    }

    /**
     * 根据display获取模型下的字段集合
     *
     * @param modelId
     * @param display
     * @return
     */
    @GetMapping("/getFieldListByDisplay")
    public Result<List<FieldDefaultByModelIdVO>> getDisplayFieldList(@RequestParam Long modelId,
                                                                     @RequestParam(value = "display", defaultValue = "1") Byte display) {
        return Result.ok(fieldApplicationService.getDisplayField(modelId, display));
    }

    /**
     * 更新模型字段显示
     *
     * @param request
     * @return
     */
    @PostMapping("/updateFieldDisplay")
    public Result<String> updateFieldDisplay(@RequestBody UpdateFieldDisplayDTO request) {
        boolean flag = fieldApplicationService.updateFieldDisplay(request.getModelId(), request.getFieldIdList());
        if (flag) {
            return Result.ok("更新成功");
        } else {
            return Result.error("更新失败");
        }
    }

}
