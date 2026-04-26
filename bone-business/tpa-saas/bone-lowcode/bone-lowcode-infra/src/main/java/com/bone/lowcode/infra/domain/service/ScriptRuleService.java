package com.bone.lowcode.infra.domain.service;

import com.bone.lowcode.infra.infrastructure.persistence.dataobject.CfgScriptRule;
import com.bone.lowcode.infra.infrastructure.persistence.mapper.ScriptRuleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ScriptRuleService {

    @Autowired
    private ScriptRuleMapper scriptRuleMapper;


    public boolean add(CfgScriptRule scriptRule) {
        return scriptRuleMapper.insert(scriptRule) == 1;
    }
}
