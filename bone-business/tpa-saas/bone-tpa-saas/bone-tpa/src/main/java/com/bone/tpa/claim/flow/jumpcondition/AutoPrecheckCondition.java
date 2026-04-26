package com.bone.tpa.claim.flow.jumpcondition;

import com.bone.core.util.PkListUtil;
import com.bone.tpa.claim.application.enums.CommonLogType;
import com.bone.tpa.claim.domain.service.ClaimTrackLogService;
import com.bone.tpa.claim.domain.service.CommonLogService;
import com.bone.tpa.sdk.claim.enums.OperationTypeEnum;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.claim.model.ClaimTrackLog;
import com.bone.tpa.sdk.vo.FlowConditionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AutoPrecheckCondition implements  JumpConditionIntterface{
    @Autowired
    private ClaimTrackLogService trackLogService;
    @Autowired
    private CommonLogService commonLogService;
    /**
     * beanName
     *
     * @return
     */
    @Override
    public String getBeanName() {
        return "autoPrecheckCondition";
    }

    /**
     * 是否命中
     *
     * @param claim
     * @param conditionVO
     * @return
     */
    @Override
    public boolean isMatch(Claim claim, FlowConditionVO conditionVO) {
        ClaimTrackLog check = PkListUtil.first( trackLogService.
                queryClaimRecord(claim.getId(), OperationTypeEnum.PRECHECK_AUTO));
        boolean ret = check != null;
        //记录log
        commonLogService.addLogASync(claim.getId().toString(), CommonLogType.FLOW_LOG,
                this.getBeanName()+" 自动化初审条件命中:{}" , ret);
        return ret;
    }
}
