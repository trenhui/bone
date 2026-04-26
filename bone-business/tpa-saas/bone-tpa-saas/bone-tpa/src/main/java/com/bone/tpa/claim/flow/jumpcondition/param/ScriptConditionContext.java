package com.bone.tpa.claim.flow.jumpcondition.param;

import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.claim.flow.jumpcondition.JumpConditionIntterface;
import com.bone.tpa.sdk.claim.model.Claim;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;


@Data
public class ScriptConditionContext {

    private Claim claim;

    private Map<String, JumpConditionIntterface> conditionMap;


    private CommonLogService commonLogService;
}
