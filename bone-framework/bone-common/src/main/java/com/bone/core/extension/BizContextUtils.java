package com.bone.core.extension;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.bone.core.extension.extractor.BizParamExtractor;
import com.bone.core.extension.extractor.BizParamExtractorFactory;
import com.bone.core.extension.extractor.ReflectionBizParamExtractor;
import io.micrometer.common.util.StringUtils;
import org.springframework.lang.Nullable;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;


/**
 * 业务上下文工具类
 */
public class BizContextUtils {

    private static TransmittableThreadLocal<BizContext> bizCtxThreadLocal = new TransmittableThreadLocal<>();



    /**
     * 获取当前BizContext
     *
     * @return BizContext
     */
    public static BizContext getCurrentContext() {
        if (bizCtxThreadLocal.get() == null) {
            return getBizContext();
        }

        return bizCtxThreadLocal.get();
    }


    /**
     * 设置当前BizContext
     *
     * @return BizContext
     */

    @SuppressWarnings({})
    public static void setCurrentContext(BizContext<?> bizContext) {
        if (bizCtxThreadLocal.get() == null) {

            HttpServletRequest request = getRequest();
            if (request == null) {
                //非HTTP环境 从data从拿去参数
                populateParamsFromData(bizContext);
            } else {

                String tenantCode = request.getParameter("tenantCode");
                if (StringUtils.isBlank(tenantCode)) {
                    Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(
                            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE
                    );
                    tenantCode = pathVariables.get("tenantCode");
                }
                bizContext.setTenantCode(tenantCode);
                bizContext.setBizCode(request.getParameter("bizCode"));
                bizContext.setUseCase(request.getParameter("useCase"));
                bizContext.setScenario(request.getParameter("scenario"));
            }
            bizCtxThreadLocal.set(bizContext);
        }
    }




    private static void populateParamsFromData(BizContext<?> context) {
        Object data = context.getData();
        if (data == null) return;

        BizParamExtractor<Object> extractor = BizParamExtractorFactory.getExtractor(data);
        if (extractor == null) {
            extractor = new ReflectionBizParamExtractor<>();
        }

        context.setTenantCode(extractor.getTenantCode(data));
        context.setBizCode(extractor.getBizCode(data));
        context.setUseCase(extractor.getUseCase(data));
        context.setScenario(extractor.getScenario(data));
    }




    public static void removeCurrentContext() {
        bizCtxThreadLocal.remove();
    }




    @Nullable
    public static BizContext getBizContext() {


            HttpServletRequest request = getRequest();
            if (request == null) return null;

            BizContext bizContext = BizContext.builder()
                    .tenantCode(request.getParameter("tenantCode"))
                    .bizCode(request.getParameter("bizCode"))
                    .useCase(request.getParameter("useCase"))
                    .scenario(request.getParameter("scenario"))
                    .build();


            return bizContext;

    }



    public static HttpServletRequest getRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes servletRequestAttributes)) {
            return null;
        }

        return servletRequestAttributes.getRequest();
    }
}
