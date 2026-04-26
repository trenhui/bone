package com.bone.tpa.hook.impl.syncTotpa;

import com.bone.tpa.api.vo.ClaimDetailSyncVO;
import com.bone.tpa.claim.domain.ext.strategy.YongChengUpdateRule;
import com.bone.tpa.hook.inter.SyncToTpaAfterDealHook;
import com.bone.tpa.sdk.identityRule.BizidentityHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class YongchengSyncToTpaAfterDealHook extends SyncToTpaAfterDealHook implements BizidentityHook<ClaimDetailSyncVO,Boolean> {
    @Autowired
    private YongChengUpdateRule yongChengUpdateRule;
    /**
     * 执行的beanName
     *
     * @return
     */
    @Override
    public String getBeanName() {
        return "yongchengSyncToTpaAfterDealHook";
    }

    /**
     * 对bean 的功能详细描述（页面会展示）
     *
     * @return
     */
    @Override
    public String getBeanDesc() {
        return "永诚同步到TPA后置处理";
    }

    @Override
    public Boolean doEvent(ClaimDetailSyncVO param) {
        log.info("YongchengSyncToTpaAfterDealHook-start");
        yongChengUpdateRule.filterDataWhileToTpa(param);
        return true;
    }


}
