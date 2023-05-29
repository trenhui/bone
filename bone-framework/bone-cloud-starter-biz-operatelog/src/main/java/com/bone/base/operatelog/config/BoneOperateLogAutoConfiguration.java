package com.bone.base.operatelog.config;

import com.bone.base.operatelog.core.aop.OperateLogAspect;
import com.bone.base.operatelog.core.service.OperateLogFrameworkService;
import com.bone.base.operatelog.core.service.OperateLogFrameworkServiceImpl;
import com.bone.module.system.api.logger.OperateLogApi;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class BoneOperateLogAutoConfiguration {

    @Bean
    public OperateLogAspect operateLogAspect() {
        return new OperateLogAspect();
    }

    @Bean
    public OperateLogFrameworkService operateLogFrameworkService(OperateLogApi operateLogApi) {
        return new OperateLogFrameworkServiceImpl(operateLogApi);
    }

}
