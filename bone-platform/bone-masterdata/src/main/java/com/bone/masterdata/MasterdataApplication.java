package com.bone.masterdata;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import com.bone.engine.extension.api.annotation.EnableExtensionPoints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableSqlRepositories(basePackages = "com.bone.masterdata.domain.repository")
@EnableExtensionPoints(basePackages = "com.bone.masterdata.domain.service")
public class MasterdataApplication {
    public static void main(String[] args) {
        SpringApplication.run(MasterdataApplication.class, args);
    }
}
