package com.bone.core.integration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate requestTemplate) {
        // 从 RequestContext 中获取 partnerName
        String partnerCode = RequestContext.getPartnerCode();
        if (partnerCode != null) {
            requestTemplate.header("partnerCode", partnerCode);
        }
    }
}
