package com.bone.lowcode.infra;

import com.bone.lowcode.infra.domain.model.App;
import com.bone.lowcode.infra.domain.service.AppService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

import java.util.*;

/**
 * @author renhui.trh
 */
@SpringBootApplication
@ConfigurationPropertiesScan
//@Import(MyBatisJdbcConfiguration.class)
@EnableJdbcRepositories(considerNestedRepositories = true)
//@EnableJdbcRepositories
public class LowcodeInfraApplication {
    public static void main(String[] args) {
        SpringApplication.run(LowcodeInfraApplication.class, args);
    }

    @Bean
    public CommandLineRunner customQuery(ApplicationContext ctx, AppService appService) {
        return (args) -> {
            App queryApp = new App();
            queryApp.setCode("pur");
            Pageable pageParam = PageRequest.of(0,10);
            queryApp.setStatus(1);
            List<App>  list1 = appService.customQuery(queryApp, pageParam);
            System.out.println("customQuery  exec ");
        };
    }
}
