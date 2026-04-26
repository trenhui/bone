package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.dto.groupData.GetBatchDataDTO;
import com.bone.lowcode.infra.application.dto.groupData.GetDataByCodeDTO;
import com.bone.lowcode.infra.application.service.GroupDataApplicationService;
import com.bone.lowcode.infra.application.vo.PageResult;
import com.bone.lowcode.infra.application.vo.groupData.GetBatchDataVO;
import com.bone.lowcode.infra.application.vo.groupData.SelectDropDataVO;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/data")
@Validated
public class GroupDataController {

    @Autowired
    private GroupDataApplicationService groupDataApplicationService;

    /**
     * 分页获取数据类型
     */
    @GetMapping("/types/page")
    public Result getDataTypePage(@RequestParam(value = "pageNum", defaultValue = "1") Long pageNum,
                                  @RequestParam(value = "pageSize", defaultValue = "10") Long pageSize,
                                  @NotNull(message = "数据来源类型不能为空") Byte type,
                                  @RequestParam(value = "name", required = false) String name) {
        PageResult<SelectDropDataVO> result = groupDataApplicationService.getDataTypePage(pageNum, pageSize, type, name);
        return Result.ok(result);
    }


    /**
     * 根据数据类型、code获取根节点
     */
    @GetMapping("/type")
    public Result getDataType(@NotNull(message = "数据来源类型不能为空") Byte type,
                              @NotEmpty(message = "数据来源code不能为空") String code) {
        SelectDropDataVO result = groupDataApplicationService.getDataType(type, code);
        return Result.ok(result);
    }

    /**
     * 根据数据类型、父code分页获取数据
     */
    @GetMapping("/page")
    public Result getDataPage(@RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                              @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                              @NotNull(message = "数据来源类型不能为空") Byte type,
                              @NotEmpty(message = "数据来源code不能为空") String parentCode,
                              @RequestParam(value = "name", required = false) String name) {
        PageResult<SelectDropDataVO> result = groupDataApplicationService.getDataPage(pageNum, pageSize, type, parentCode, name);
        return Result.ok(result);
    }

    /**
     * 根据数据类型、父code批量获取数据
     */
    @PostMapping("/batch")
    public Result getBatchData(@Validated @RequestBody GetBatchDataDTO param) {
        List<GetBatchDataVO> result = groupDataApplicationService.getBatchData(param);
        return Result.ok(result);
    }

    /**
     * 根据数据类型、codeList获取值
     */
    @PostMapping("/listByCode")
    public Result getDataByCode(@Validated @RequestBody GetDataByCodeDTO param) {
        List<SelectDropDataVO> result = groupDataApplicationService.getValueListByCodeList(param.getType(), param.getRootCode(), param.getCodeList());
        return Result.ok(result);
    }
}
