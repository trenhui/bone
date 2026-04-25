package com.bone.blueprint;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import com.bone.engine.extension.api.annotation.EnableExtensionPoints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@EnableSqlRepositories(basePackages = "com.bone.blueprint.domain.repository")
@EnableExtensionPoints(basePackages = "com.bone.blueprint.domain.service")
public class BoneBlueprintApplication {

    public static void main(String[] args) {
        SpringApplication.run(BoneBlueprintApplication.class, args);
    }
}



