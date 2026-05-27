package com.bone.blueprint;

import com.bone.engine.extension.api.annotation.EnableExtensionPoints;
import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"com.bone.blueprint", "com.bone.core.capability", "com.bone.web"})
@EnableDiscoveryClient
@EnableScheduling
@EnableSqlRepositories(basePackages = "com.bone.blueprint.domain.repository")
@EnableExtensionPoints(basePackages = "com.bone.blueprint.domain.service")
public class BoneBlueprintApplication {

    public static void main(String[] args) {
        SpringApplication.run(BoneBlueprintApplication.class, args);
    }
}



