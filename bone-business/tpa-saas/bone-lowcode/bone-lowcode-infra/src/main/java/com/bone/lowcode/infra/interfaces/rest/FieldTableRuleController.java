package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.dto.fieldTableRule.CreateFieldTableRuleDTO;
import com.bone.lowcode.infra.application.dto.fieldTableRule.UpdateFieldTableRuleDTO;
import com.bone.lowcode.infra.application.service.FieldTableRuleApplicationService;
import com.bone.lowcode.infra.application.vo.fieldTableRule.FieldTableRuleVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/fieldTableRule")
@Validated
public class FieldTableRuleController {

    @Autowired
    private FieldTableRuleApplicationService applicationService;


    /**
     * 创建字段-表格联动规则
     */
    @PostMapping("/create")
    public Result create(@Validated @RequestBody CreateFieldTableRuleDTO dto) {
        boolean flag = applicationService.create(dto);
        if (flag) return Result.ok("创建成功!");
        else return Result.error("创建失败!");
    }

    /**
     * 获取当前字段主导的字段-表格联动规则
     */
    @GetMapping("/getRuleByFieldId")
    public Result getRuleByFieldId(@RequestParam("fieldId") Long fieldId) {
        List<FieldTableRuleVO> voList = applicationService.getRuleByFieldId(fieldId);
        return Result.ok(voList);
    }

    /**
     * 获取指定页面的字段-表格联动规则
     */
    @GetMapping("/getRuleByPageCodeAndBizIdentityCode")
    public Result getRuleByPageCodeAndBizIdentityCode(@RequestParam("pageCode") String pageCode,
                                                      @RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        List<FieldTableRuleVO> voList = applicationService.getRuleByPageCodeAndBizIdentityCode(pageCode, bizIdentityCode);
        return Result.ok(voList);
    }

    /**
     * 更新字段-表格联动规则
     */
    @PostMapping("/update")
    public Result update(@Validated @RequestBody UpdateFieldTableRuleDTO param) {
        boolean flag = applicationService.update(param);
        if (flag) return Result.ok("更新成功!");
        else return Result.error("更新失败!");
    }

    /**
     * 删除字段-表格联动规则
     */
    @GetMapping("/deleteById")
    public Result deleteById(@RequestParam("ruleId") Long id) {
        boolean flag = applicationService.deleteById(id);
        if (flag) return Result.ok("删除成功!");
        else return Result.error("删除失败!");
    }
}
