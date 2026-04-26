package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.dto.optionSet.*;
import com.bone.lowcode.infra.application.service.OptionSetApplicationService;
import com.bone.lowcode.infra.application.vo.PageResult;
import com.bone.lowcode.infra.application.vo.optionSet.FieldLinkedDisplayRuleVO;
import com.bone.lowcode.infra.application.vo.optionSet.OptionSetInfoVO;
import com.bone.lowcode.infra.application.vo.optionSet.OptionSetVO;
import com.bone.lowcode.infra.application.vo.optionSet.OptionValuePageVO;
import com.bone.lowcode.infra.infrastructure.common.annotation.HandleRepeatedReq;
import com.bone.lowcode.infra.infrastructure.common.annotation.KeyPart;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/optionSet")
@Validated
public class OptionSetController {

    @Autowired
    private OptionSetApplicationService optionSetApplicationService;

    @HandleRepeatedReq(true)
    @GetMapping("/testGet")
    public Result testGet(@KeyPart @NotNull(message = "当前页码不能为空") Integer pageNum,
                          @KeyPart(3) @NotNull(message = "每页记录数不能为空") Integer pageSize,
                          @KeyPart(2) @NotNull(message = "总数不能为空") Integer total) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Result.ok("testGet");
    }

    @HandleRepeatedReq(true)
    @PostMapping("/testPost")
    public Result testPost(@RequestBody TestPostDTO dto) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Result.ok("testPost");
    }

    @Data
    private static class TestPostDTO {
        @KeyPart(3)
        private Integer a;

        @KeyPart(2)
        private String b;

        @KeyPart(1)
        private Integer c;
    }

    /**
     * 查询选项集列表
     */
    @GetMapping("/getSetList")
    public Result getSetList(@NotNull(message = "当前页码不能为空") Long pageNum,
                             @NotNull(message = "每页记录数不能为空") Long pageSize) {
        PageResult<OptionSetVO> result = optionSetApplicationService.getSetList(pageNum, pageSize);
        return Result.ok(result);
    }

    /**
     * 查询选项集详情
     */
    @GetMapping("/getSetDetail")
    public Result getSetDetail(@NotNull(message = "选项集id不能为空") Long optionSetId) {
        OptionSetInfoVO optionSetInfoVO = optionSetApplicationService.getSetDetail(optionSetId);
        return Result.ok(optionSetInfoVO);
    }

    /**
     * 选项值分页
     */
    @GetMapping("/valuePage")
    public Result valuePage(@NotNull(message = "选项集id不能为空") Long optionSetId,
                            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize,
                            @RequestParam(value = "optionCode", required = false) String optionCode,
                            @RequestParam(value = "optionName", required = false) String optionName) {
        OptionValuePageVO vo = optionSetApplicationService.valuePage(optionSetId, pageNum, pageSize, optionCode, optionName);
        return Result.ok(vo);
    }

    /**
     * 新增单个选项值
     */
    @PostMapping("/addValue")
    public Result addValue(@Validated @RequestBody InsertValue param) {
        boolean flag = optionSetApplicationService.addValue(param);
        if (flag) return Result.ok("新增完成!");
        else return Result.error("新增失败!");
    }

    /**
     * 删除单个选项值
     */
    @GetMapping("/deleteValue")
    public Result deleteValue(@NotNull(message = "选项值id不能为空") Long valueId) {
        boolean flag = optionSetApplicationService.deleteValue(valueId);
        if (flag) return Result.ok("删除完成!");
        else return Result.error("删除失败!");
    }

    /**
     * 更新选项集
     */
    @PostMapping("/updateOptionSet")
    public Result updateOptionSet(@Validated @RequestBody UpdateOptionSetDTO param) {
        boolean flag = optionSetApplicationService.updateOptionSet(param);
        if (flag) return Result.ok("更新完成!");
        else return Result.error("更新失败!");
    }

    /**
     * 更新单个选项值
     */
    @PostMapping("/updateValue")
    public Result updateValue(@Validated @RequestBody UpdateValue param) {
        boolean flag = optionSetApplicationService.updateValue(param);
        if (flag) return Result.ok("更新完成!");
        else return Result.error("更新失败!");
    }

    /**
     * 创建选项集
     */
    @PostMapping("/addOptionSet")
    public Result addOptionSet(@Validated @RequestBody AddOptionSetDTO param) {
        Boolean flag = optionSetApplicationService.addOptionSet(param);
        if (flag) return Result.ok("更新完成!");
        else return Result.error("更新失败!");
    }

    /**
     * 删除选项集
     */
    @GetMapping("/delete")
    public Result deleteOptionSet(@RequestParam("optionSetId") Long optionSetId) {
        Boolean flag = optionSetApplicationService.deleteOptionSet(optionSetId);
        if (flag) return Result.ok("删除完成!");
        else return Result.error("删除失败!");
    }

    //-----------------------------字段联动展示规则↓------------------------------------------------

    /**
     * 增、删、改字段联动展示规则
     */
    @PostMapping("/updateFieldLinkedDisplayRule")
    public Result updateFieldLinkedDisplayRule(@Validated @RequestBody FieldLinkedDisplayRuleDTO param) {
        boolean flag = optionSetApplicationService.updateFieldLinkedDisplayRule(param);
        if (flag) return Result.ok("更新完成!");
        else return Result.error("更新失败!");
    }

    /**
     * 查询字段联动展示规则
     */
    @GetMapping("/getFieldLinkedDisplayRule")
    public Result getFieldLinkedDisplayRule(@RequestParam("selectFieldId") Long selectFieldId,
                                            @RequestParam("datasourceType") Byte datasourceType,
                                            @RequestParam("datasourceCode") String datasourceCode) {
        List<FieldLinkedDisplayRuleVO> list = optionSetApplicationService.getFieldLinkedDisplayRule(selectFieldId, datasourceType, datasourceCode);
        return Result.ok(list);
    }
}
