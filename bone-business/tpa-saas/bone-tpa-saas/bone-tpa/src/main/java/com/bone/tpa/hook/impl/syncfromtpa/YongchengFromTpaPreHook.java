package com.bone.tpa.hook.impl.syncfromtpa;

import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import com.bone.tpa.claim.domain.ext.strategy.YongChengUpdateRule;
import com.bone.tpa.hook.inter.SyncFromTpaPreDealHook;
import com.bone.tpa.hook.vo.FromTpaPreHookParam;
import com.bone.tpa.sdk.identityRule.BizidentityHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class YongchengFromTpaPreHook extends SyncFromTpaPreDealHook   implements BizidentityHook<FromTpaPreHookParam,Boolean> {
    @Autowired
    private    YongChengUpdateRule yongChengUpdateRule;
    /**
     * 执行的beanName
     *
     * @return
     */
    @Override
    public String getBeanName() {
        return "yongchengFromTpaPreHook";
    }

    /**
     * 对bean 的功能详细描述（页面会展示）
     *
     * @return
     */
    @Override
    public String getBeanDesc() {
        return "永诚从tpa同步数据前处理";
    }

    @Override
    public Boolean doEvent(FromTpaPreHookParam param) {
        log.info("YongchengFromTpaPreHook-start");
        yongChengUpdateRule.onUpdateClaimWhileFromTpa(param.getSyncVO(),
                param.getBizIdentityCode(),param.getContext(),param.getHintMap());
        return true;
    }

}
