package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.dto.scriptRule.CreateScriptRuleDTO;
import com.bone.lowcode.infra.application.service.ScriptRuleApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scriptRule")
public class ScriptRuleController {

    @Autowired
    private ScriptRuleApplicationService scriptRuleApplicationService;


    /**
     * 新增脚本规则
     */
//    @PostMapping("/add")
    public Result add(@Validated @RequestBody CreateScriptRuleDTO param) {
        boolean flag = scriptRuleApplicationService.add(param);
        if (flag) return Result.ok("新增完成!");
        else return Result.error("新增失败!");
    }
}
