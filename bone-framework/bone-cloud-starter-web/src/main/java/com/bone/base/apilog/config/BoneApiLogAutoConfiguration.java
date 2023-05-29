package com.bone.base.apilog.config;

import com.bone.base.apilog.core.filter.ApiAccessLogFilter;
import com.bone.base.apilog.core.service.ApiAccessLogFrameworkService;
import com.bone.base.apilog.core.service.ApiAccessLogFrameworkServiceImpl;
import com.bone.base.apilog.core.service.ApiErrorLogFrameworkService;
import com.bone.base.apilog.core.service.ApiErrorLogFrameworkServiceImpl;
import com.bone.base.core.enums.WebFilterOrderEnum;
import com.bone.base.web.config.WebProperties;
import com.bone.base.web.config.BoneWebAutoConfiguration;
import com.bone.infra.api.logger.ApiAccessLogApi;
import com.bone.infra.api.logger.ApiErrorLogApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import jakarta.servlet.Filter;

@AutoConfiguration
@AutoConfigureAfter(BoneWebAutoConfiguration.class)
public class BoneApiLogAutoConfiguration {

//    @Bean //todo meishan
//    public ApiAccessLogFrameworkService apiAccessLogFrameworkService(ApiAccessLogApi apiAccessLogApi) {
//        return new ApiAccessLogFrameworkServiceImpl(apiAccessLogApi);
//    }

    @Bean
    public ApiAccessLogFrameworkService apiAccessLogFrameworkService() {
        return new ApiAccessLogFrameworkServiceImpl();
    }



//    @Bean
//    public ApiErrorLogFrameworkService apiErrorLogFrameworkService(ApiErrorLogApi apiErrorLogApi) {
//        //todo meishan
//        //return new ApiErrorLogFrameworkServiceImpl(apiErrorLogApi);
//        return new ApiErrorLogFrameworkServiceImpl();
//    }

    @Bean
    public ApiErrorLogFrameworkService apiErrorLogFrameworkService() {
        //todo meishan
        //return new ApiErrorLogFrameworkServiceImpl(apiErrorLogApi);
        return new ApiErrorLogFrameworkServiceImpl();
    }

    /**
     * 创建 ApiAccessLogFilter Bean，记录 API 请求日志
     */
    @Bean
    @ConditionalOnProperty(prefix = "bone.access-log", value = "enable", matchIfMissing = true) // 允许使用 bone.access-log.enable=false 禁用访问日志
    public FilterRegistrationBean<ApiAccessLogFilter> apiAccessLogFilter(WebProperties webProperties,
                                                                         @Value("${spring.application.name}") String applicationName,
                                                                         ApiAccessLogFrameworkService apiAccessLogFrameworkService) {
        ApiAccessLogFilter filter = new ApiAccessLogFilter(webProperties, applicationName, apiAccessLogFrameworkService);
        return createFilterBean(filter, WebFilterOrderEnum.API_ACCESS_LOG_FILTER);
    }

    private static <T extends Filter> FilterRegistrationBean<T> createFilterBean(T filter, Integer order) {
        FilterRegistrationBean<T> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(order);
        return bean;
    }

}
