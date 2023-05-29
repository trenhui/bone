package com.bone.base.apilog.core.service;

import cn.hutool.core.bean.BeanUtil;
import com.bone.infra.api.logger.ApiErrorLogApi;
import com.bone.infra.api.logger.dto.ApiErrorLogCreateReqDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;

/**
 * API 错误日志 Framework Service 实现类
 * <p>
 * 基于 {@link ApiErrorLogApi} 远程服务，记录错误日志
 *
 * @author 芋道源码
 */
//@RequiredArgsConstructor   //todo meishan
public class ApiErrorLogFrameworkServiceImpl implements ApiErrorLogFrameworkService {
    //todo meishan
    //private final ApiErrorLogApi apiErrorLogApi;

    @Override
    @Async
    public void createApiErrorLog(ApiErrorLog apiErrorLog) {
        ApiErrorLogCreateReqDTO reqDTO = BeanUtil.copyProperties(apiErrorLog, ApiErrorLogCreateReqDTO.class);
        //todo meishan
        //apiErrorLogApi.createApiErrorLog(reqDTO).checkError();
    }

}
