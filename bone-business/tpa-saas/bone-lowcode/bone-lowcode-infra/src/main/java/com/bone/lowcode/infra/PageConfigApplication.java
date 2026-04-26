package com.bone.lowcode.infra;


import com.bone.lowcode.infra.application.task.AlertRobotManager;
import com.bone.core.util.SpringContextUtils;
import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * @author renhui.trh
 */
//@EnableApolloConfig
@SpringBootApplication
@MapperScan("com.bone.lowcode.infra.infrastructure.persistence.mapper")
@ConfigurationPropertiesScan
//@EnableApolloConfig
@EnableAspectJAutoProxy
@EnableFeignClients(basePackages = {"com.bone.lowcode.infra.infrastructure.feign"})
public class PageConfigApplication {

    private static ApplicationContext context;

    public static ApplicationContext getContext() {
        return context;
    }

    public static void main(String[] args) {
        context = SpringApplication.run(PageConfigApplication.class, args);

        SpringContextUtils.getBean(AlertRobotManager.class).doAlertAsyncDefault("pageConfig启动成功");
    }
}
