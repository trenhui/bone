package com.bone.core.integration;

import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class RequestContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 从请求头中获取 partnerName
        String partnerCode = request.getHeader("partnerCode");

        // 设置到 RequestContext
        if (partnerCode != null) {
            RequestContext.setPartnerCode(partnerCode);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 清理上下文
        RequestContext.clear();
    }
}
