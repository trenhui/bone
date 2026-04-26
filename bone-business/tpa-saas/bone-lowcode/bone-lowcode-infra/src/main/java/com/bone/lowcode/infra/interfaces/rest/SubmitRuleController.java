package com.bone.lowcode.infra.interfaces.rest;

import com.bone.core.result.Result;
import com.bone.lowcode.infra.application.dto.submitRule.CreateSubmitRuleDTO;
import com.bone.lowcode.infra.application.dto.submitRule.UpdateSubmitRuleDTO;
import com.bone.lowcode.infra.application.service.SubmitRuleApplicationService;
import com.bone.lowcode.infra.application.vo.submitRule.GetSubmitRuleByPageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/submitRule")
public class SubmitRuleController {

    @Autowired
    private SubmitRuleApplicationService applicationService;

    /**
     * 创建提交校验规则
     */
    @PostMapping("/create")
    public Result<String> create(@RequestBody CreateSubmitRuleDTO dto) {
        boolean flag = applicationService.create(dto);
        if (flag) return Result.ok("创建提交校验规则成功!");
        else return Result.error("创建提交校验规则失败!");
    }

    /**
     * 获取指定页面的提交校验规则列表
     */
    @GetMapping("/getRuleByPage")
    public Result getRuleByPage(@RequestParam("pageCode") String pageCode,
                                @RequestParam(value = "bizIdentityCode", required = false) String bizIdentityCode) {
        List<GetSubmitRuleByPageVO> voList = applicationService.getRuleByPage(pageCode, bizIdentityCode);
        return Result.ok(voList);
    }

    /**
     * 更新提交校验规则
     */
    @PostMapping("/update")
    public Result<String> update(@RequestBody UpdateSubmitRuleDTO dto) {
        boolean flag = applicationService.update(dto);
        if (flag) return Result.ok("更新提交校验规则成功!");
        else return Result.error("更新提交校验规则失败!");
    }

    /**
     * 删除指定的提交校验规则列表
     */
    @GetMapping("/deleteById")
    public Result deleteById(@RequestParam("submitRuleId") Long id) {
        boolean flag = applicationService.deleteById(id);
        if (flag) return Result.ok("删除成功!");
        else return Result.error("删除失败!");
    }
}
