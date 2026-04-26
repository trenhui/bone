package com.bone.tpa;

import com.bone.core.util.SpringContextUtils;
import com.bone.tpa.core.synctask.AlertRobotManager;
import com.ctrip.framework.apollo.spring.annotation.EnableApolloConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * @author renhui.trh
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableFeignClients
//@EnableApolloConfig
@MapperScan(basePackages = "com.bone.tpa.sdk.masterdb.mapper", sqlSessionFactoryRef = "masterdbSqlSessionFactory")
@MapperScan(basePackages = "com.bone.tpa.sdk.tpasaasdb.mapper", sqlSessionFactoryRef = "tpasaasSessionFactory")
@EnableAsync
@EnableScheduling
public class TpaApplication {
    public static void main(String[] args) {
        SpringApplication.run(TpaApplication.class, args);

        SpringContextUtils.getBean(AlertRobotManager.class).doAlertAsyncDefault("saas启动成功");
    }


    @Bean(name = "remoteRestTemplate")
    public RestTemplate remoteRestTemplate() {
        return new RestTemplate();
    }
}
