package com.bone.tpa.hook.impl.claim;

import com.bone.tpa.claim.domain.ext.strategy.YongChengStrategy;
import com.bone.tpa.hook.inter.ClaimPageSaveValidHook;
import com.bone.tpa.sdk.claim.model.Claim;
import com.bone.tpa.sdk.identityRule.BizidentityHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class YongchengPageSaveValidHook extends ClaimPageSaveValidHook implements BizidentityHook<Claim,Boolean> {
    @Autowired
    private YongChengStrategy claimValidationExt;
    /**
     * 执行的beanName
     *
     * @return
     */
    @Override
    public String getBeanName() {
        return "yongchengPageSaveValidHook";
    }

    /**
     * 对bean 的功能详细描述（页面会展示）
     *
     * @return
     */
    @Override
    public String getBeanDesc() {
        return "永诚页面保存校验规则";
    }

    @Override
    public Boolean doEvent(Claim param) {
        log.info("YongchengPageSaveValidHook start");

        claimValidationExt.validate(param);
        return true;
    }
}
