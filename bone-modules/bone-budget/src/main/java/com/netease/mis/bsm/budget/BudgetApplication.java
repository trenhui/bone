package com.netease.mis.bsm.budget;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

/**
 * @author 梅山源码
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJdbcRepositories(considerNestedRepositories = true)
public class BudgetApplication {
    public static void main(String[] args) {
        SpringApplication.run(BudgetApplication.class, args);
    }
}
