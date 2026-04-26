package com.bone.tpa.hook.impl.claim;

import com.bone.tpa.claim.domain.ext.strategy.YongChengUpdateRule;
import com.bone.tpa.hook.inter.ClaimPageSaveAfterHook;
import com.bone.tpa.hook.vo.ClaimUpdateHookParam;
import com.bone.tpa.sdk.identityRule.BizidentityHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class YongchengClaimUpdateAfterHookPage extends ClaimPageSaveAfterHook implements BizidentityHook<ClaimUpdateHookParam,Boolean> {
    @Autowired
    YongChengUpdateRule yongChengUpdateRule;
    /**
     * 执行的beanName
     *
     * @return
     */
    @Override
    public String getBeanName() {
        return "YongchengClaimUpdateHook";
    }

    /**
     * 对bean 的功能详细描述（页面会展示）
     *
     * @return
     */
    @Override
    public String getBeanDesc() {
        return "永诚赔案保存处理";
    }

    @Override
    public Boolean doEvent(ClaimUpdateHookParam param) {
        log.info("YongchengClaimUpdateHook start");
        yongChengUpdateRule.onUpdateClaim(param.getClaimExist(), param.getClaimDetailObject());
        return true;
    }


}
