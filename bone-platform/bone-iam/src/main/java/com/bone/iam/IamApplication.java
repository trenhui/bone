package com.bone.iam;

import com.bone.metadata.sdk.domain.annotation.EnableSqlRepositories;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {"com.bone.iam", "com.bone.metadata.sdk"})
@EnableSqlRepositories(basePackages = "com.bone.iam.domain.repository")
@ConfigurationPropertiesScan
public class IamApplication {
    public static void main(String[] args) {
        SpringApplication.run(IamApplication.class, args);
    }
}
