package com.bone.tpa.hook.impl.invoice;

import com.bone.tpa.claim.domain.ext.strategy.YongChengUpdateRule;
import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.identityRule.BizidentityHook;
import com.bone.tpa.sdk.identityRule.invoice.update.OnSaveInvoiceHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service()
public class YongchengInvoiceSaveHook extends OnSaveInvoiceHook implements BizidentityHook<ClaimInvoice,Boolean> {
    @Autowired
    YongChengUpdateRule yongChengUpdateRule;
    /**
     * 执行的beanName
     *
     * @return
     */
    @Override
    public String getBeanName() {
        return "YongchengInvoiceSaveHook";
    }

    /**
     * 对bean 的功能详细描述（页面会展示）
     *
     * @return
     */
    @Override
    public String getBeanDesc() {
        return "永诚发票保存处理";
    }

    @Override
    public Boolean doEvent(ClaimInvoice param) {
        log.info("YongchengInvoiceSaveHook-start");
        yongChengUpdateRule.onUpdateInvoice(param);
        return true;
    }

}
