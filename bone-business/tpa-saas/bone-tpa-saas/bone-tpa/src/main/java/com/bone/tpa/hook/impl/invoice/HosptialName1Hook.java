package com.bone.tpa.hook.impl.invoice;

import com.bone.tpa.sdk.claim.model.ClaimInvoice;
import com.bone.tpa.sdk.identityRule.BizidentityHook;
import com.bone.tpa.sdk.identityRule.invoice.hospitalName.InvoiceHosptialCalHook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
@Slf4j
@Service
public class HosptialName1Hook extends InvoiceHosptialCalHook implements BizidentityHook<String,String> {
    /**
     * 执行的beanName
     *
     * @return
     */
    @Override
    public String getBeanName() {
        return "HosptialName1Hook";
    }

    /**
     * 对bean 的功能详细描述（页面会展示）
     *
     * @return
     */
    @Override
    public String getBeanDesc() {
        return "默认医院名称: 本代码表中不存在的其他医院";
    }

    @Override
    public String doEvent(String bizIdentityCode) {
        log.info("HosptialName1Hook-医院名称: 本代码表中不存在的其他医院");
        return "本代码表中不存在的其他医院";
    }


}
