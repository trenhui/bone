package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.dto.fieldLinkageRule.CreateFieldLinkageRuleDTO;
import com.bone.lowcode.infra.application.dto.fieldLinkageRule.UpdateFieldLinkageRuleDTO;
import com.bone.lowcode.infra.application.service.FieldLinkageRuleApplicationService;
import com.bone.lowcode.infra.application.vo.fieldLinkageRule.FieldRuleVOByFieldId;
import com.bone.lowcode.infra.application.vo.fieldLinkageRule.FieldRuleVOByPageId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/fieldLinkageRule")
@Validated
public class FieldLinkageRuleController {

    @Autowired
    private FieldLinkageRuleApplicationService applicationService;


    /**
     * 创建字段-字段联动规则
     */
    @PostMapping("/create")
    public Result<String> create(@RequestBody CreateFieldLinkageRuleDTO dto) {
        boolean flag = applicationService.create(dto);
        if (flag) return Result.ok("创建字段联动规则成功!");
        else return Result.error("创建字段联动规则失败!");
    }

    /**
     * 获取当前字段主导的字段-字段联动规则
     */
    @GetMapping("/getRuleByFieldId")
    public Result getRuleByFieldId(@RequestParam("fieldId") Long fieldId) {
        List<FieldRuleVOByFieldId> voList = applicationService.getRuleByFieldId(fieldId);
        return Result.ok(voList);
    }

    /**
     * 获取指定页面的字段-字段联动规则
     */
    @GetMapping("/getRuleByPageCodeAndBizIdentityCode")
    public Result getRuleByPageCodeAndBizIdentityCode(@RequestParam("pageCode") String pageCode,
                                                      @RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        List<FieldRuleVOByPageId> voList = applicationService.getRuleByPageCodeAndBizIdentityCode(pageCode, bizIdentityCode);
        return Result.ok(voList);
    }

    /**
     * 更新字段-字段联动规则
     */
    @PostMapping("/update")
    public Result<String> update(@RequestBody UpdateFieldLinkageRuleDTO dto) {
        boolean flag = applicationService.update(dto);
        if (flag) return Result.ok("更新字段联动规则成功!");
        else return Result.error("更新字段联动规则失败!");
    }

    /**
     * 删除字段-字段联动规则
     */
    @GetMapping("/deleteById")
    public Result deleteById(@RequestParam("ruleId") Long id) {
        boolean flag = applicationService.deleteById(id);
        if (flag) return Result.ok("删除成功!");
        else return Result.error("删除失败!");
    }
}
