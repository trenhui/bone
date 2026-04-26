package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.dto.model.UpdateModelDTO;
import com.bone.lowcode.infra.application.service.ModelApplicationService;
import com.bone.lowcode.infra.application.vo.PageResult;
import com.bone.lowcode.infra.application.vo.model.FieldByModelIdVO;
import com.bone.lowcode.infra.application.vo.model.GetAllModelByPageCodeVo;
import com.bone.lowcode.infra.application.vo.model.GetAllModelVo;
import com.bone.lowcode.infra.application.vo.model.SimpleModelInfo;
import com.bone.lowcode.infra.infrastructure.common.annotation.HandleRepeatedReq;
import com.bone.lowcode.infra.infrastructure.common.annotation.KeyPart;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgModelDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/model")
public class ModelController {

    @Autowired
    private ModelApplicationService modelApplicationService;


    /**
     * 查询基础页面或专属页面所有的模型
     */
    @GetMapping("/listByPageCode")
    public Result getAllModelByPageCode(@RequestParam("pageCode") String pageCode,
                                        @RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        List<GetAllModelByPageCodeVo> voList = modelApplicationService.getAllModelByPageCode(pageCode, bizIdentityCode);
        return Result.ok(voList);
    }

    /**
     * 查询某fieldSet下所有的模型
     */
    @GetMapping("/listByFieldSetId")
    public Result listByFieldSetId(@RequestParam("fieldSetId") Long fieldSetId) {
        List<SimpleModelInfo> voList = modelApplicationService.listByFieldSetId(fieldSetId);
        return Result.ok(voList);
    }

    /**
     * 查询某table下所有的模型
     */
    @GetMapping("/listByTableId")
    public Result listByTableId(@RequestParam("tableId") Long tableId) {
        List<SimpleModelInfo> voList = modelApplicationService.listByTableId(tableId);
        return Result.ok(voList);
    }

    /**
     * 数据模型管理-查询所有的模型
     */
    @GetMapping("/list")
    public Result getAllModel(@RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
                              @RequestParam(value = "pageSize", defaultValue = "10") Long pageSize,
                              @RequestParam(value = "modelName", required = false) String modelName,
                              @RequestParam(value = "modelCode", required = false) String modelCode) {
        PageResult<GetAllModelVo> result = modelApplicationService.getAllModel(pageNum, pageSize, modelName, modelCode);
        return Result.ok(result);
    }

    /**
     * 模型数据管理-查询某model下的所有字段
     */
    @GetMapping("/listByModelCode")
    public Result getFieldByModelCode(@RequestParam("modelCode") String modelCode,
                                      @RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
                                      @RequestParam(value = "pageSize", defaultValue = "10") Long pageSize,
                                      @RequestParam(value = "fieldName", required = false) String fieldName,
                                      @RequestParam(value = "componentType", required = false) String componentType) {
        PageResult<FieldByModelIdVO> result = modelApplicationService.getFieldByModelId(modelCode, pageNum, pageSize, fieldName, componentType);
        return Result.ok(result);
    }

    /**
     * 修改模型的使用状态
     */
    @PostMapping("/updateStatus")
    public Result<String> update(@RequestBody UpdateModelDTO dto) {
        boolean flag = modelApplicationService.update(dto);
        if (flag) return Result.ok();
        else return Result.error("更新失败！");
    }

    /**
     * 数据模型管理-获取元数据来更新模板页面所有模型下的字段
     */
    @HandleRepeatedReq
    @GetMapping("/updateFieldWithMetaData")
    public Result updateAllFieldWithMetaData() {
        boolean flag = modelApplicationService.updateAllFieldWithMetaData();
        if (flag) return Result.ok("更新完成!");
        else return Result.error("更新失败!");
    }

    /**
     * 数据模型管理-获取元数据来更新指定主体下的基础字段
     */
    @HandleRepeatedReq(true)
    @GetMapping("/updateFieldWithMetaDataByIdentityCode")
    public Result updateFieldWithMetaDataByIdentityCode(@KeyPart @RequestParam("bizIdentityCode") String identityCode) {
        boolean flag = modelApplicationService.updateFieldWithMetaDataByIdentityCode(identityCode);
        if (flag) return Result.ok("更新完成!");
        else return Result.error("更新失败!");
    }

    /**
     * 数据模型管理-获取元数据来更新基础模板页面的某个模型下的字段
     */
    @HandleRepeatedReq(true)
    @GetMapping("/updateSingleFieldWithMetaData")
    public Result updateSingleFieldWithMetaData(@KeyPart @RequestParam("modelId") Long modelId) {
        boolean flag = modelApplicationService.updateSingleFieldWithMetaData(modelId);
        if (flag) return Result.ok("更新完成!");
        else return Result.error("更新失败!");
    }

    /**
     * 更新模型
     */
    @PostMapping("/updateById")
    public Result update(@RequestBody CfgModelDO model) {
        boolean flag = modelApplicationService.updateById(model);
        if (flag) return Result.ok("更新完成!");
        else return Result.error("更新失败!");
    }

    /**
     * 给table、fieldSet设置模型
     */
    @GetMapping("/setModel")
    public Result setModel(@RequestParam("type") String type,
                           @RequestParam("id") Long id,
                           @RequestParam("modelCode") String modelCode) {
        boolean flag = modelApplicationService.setModel(type, id, modelCode);
        if (flag) return Result.ok("设置完成!");
        else return Result.error("设置失败!");
    }

    /**
     * 专属页面的数据模型列表
     *
     * @param bizIdentityCode
     * @return
     */
    @GetMapping("/listByBizIdentityCode")
    public Result<List<SimpleModelInfo>> listByBizIdentityCode(@RequestParam("bizIdentityCode") String bizIdentityCode) {
        return Result.ok(modelApplicationService.listByBizIdentityCode(bizIdentityCode));
    }

    /**
     * 根据模型名称获取某一page下的模型
     *
     * @param pageCode
     * @param bizIdentityCode
     * @param modelName
     * @return
     */
    @GetMapping("/getModelByName")
    public Result<SimpleModelInfo> getModelByName(@RequestParam("pageCode") String pageCode,
                                                  @RequestParam("bizIdentityCode") String bizIdentityCode,
                                                  @RequestParam(value = "modelName", defaultValue = "赔案信息") String modelName) {
        return Result.ok(modelApplicationService.getModelByName(pageCode, bizIdentityCode, modelName));
    }


}
