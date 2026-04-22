
package com.bone.blueprint;

import com.bone.engine.extension.api.annotation.EnableExtensionPoints;
import com.bone.metadata.sdk.annotation.EnableSqlRepositories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableSqlRepositories(basePackages = "com.bone.blueprint.domain.repository")
@EnableExtensionPoints(basePackages = "com.bone.blueprint.domain.service")
public class BoneBlueprintApplication {

    public static void main(String[] args) {
        SpringApplication.run(BoneBlueprintApplication.class, args);
    }
}

