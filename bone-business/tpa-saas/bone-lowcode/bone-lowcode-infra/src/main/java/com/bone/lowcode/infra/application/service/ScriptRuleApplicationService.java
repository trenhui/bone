package com.bone.lowcode.infra.application.service;

import com.bone.lowcode.infra.application.dto.scriptRule.CreateScriptRuleDTO;
import com.bone.lowcode.infra.domain.service.ScriptRuleService;
import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgScriptRule;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ScriptRuleApplicationService {

    @Autowired
    private ScriptRuleService scriptRuleService;


    public boolean add(CreateScriptRuleDTO param) {
        CfgScriptRule scriptRule = new CfgScriptRule();
        BeanUtils.copyProperties(param, scriptRule);
        return scriptRuleService.add(scriptRule);
    }
}
