package com.bone.lowcode.integration.uitls;

import org.springframework.web.context.WebApplicationContext;

public class WebApplicationContextUtils {

    private static WebApplicationContext context;

    // 设置 WebApplicationContext
    public static void setWebApplicationContext(WebApplicationContext webApplicationContext) {
        context = webApplicationContext;
    }

    // 通过 WebApplicationContext 获取 Bean
    public static <T> T getBean(Class<T> beanClass) {
        return context.getBean(beanClass);
    }
}
