package com.bone.metadata.sdk.support.config;

import com.bone.metadata.sdk.domain.enums.DeploymentMode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import lombok.Data;

@ConfigurationProperties(prefix = "metadata.sdk")
public class MetadataSdkProperties {

    private String appcode;

    private DeploymentMode deploymentMode = DeploymentMode.EMBEDDED;

    private long slowQueryThreshold = 3000; // 3s in milliseconds
    
    // 手动添加getter方法
    public String getAppcode() {
        return appcode;
    }
    
    public void setAppcode(String appcode) {
        this.appcode = appcode;
    }
    
    public DeploymentMode getDeploymentMode() {
        return deploymentMode;
    }
    
    public void setDeploymentMode(DeploymentMode deploymentMode) {
        this.deploymentMode = deploymentMode;
    }
    
    public long getSlowQueryThreshold() {
        return slowQueryThreshold;
    }
    
    public void setSlowQueryThreshold(long slowQueryThreshold) {
        this.slowQueryThreshold = slowQueryThreshold;
    }

    private Service service = new Service();

    @Value("${spring.application.name:extTest}")
    private String defaultAppcode;

    @PostConstruct
    public void init() {
        if (this.appcode == null || this.appcode.isBlank()) {
            this.appcode = defaultAppcode;
        }
    }

    @Data
    public static class Service {
        private Config config = new Config();

        @Data
        public static class Config {
            private Remote remote = new Remote();
            private Embedded embedded = new Embedded();

            @Data
            public static class Remote {
                private String authMode = "api-key";

                private String endpoint = "http://localhost:9001";

                private String token = "test-token";

                private int timeout = 3000;
            }

            @Data
            public static class Embedded {
                private int cacheTtl = 600;
            }
        }
    }
}