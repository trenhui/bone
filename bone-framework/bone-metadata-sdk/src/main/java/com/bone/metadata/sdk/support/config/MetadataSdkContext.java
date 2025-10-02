package com.bone.metadata.sdk.support.config;

import com.bone.metadata.sdk.domain.enums.DatabaseType;
import com.bone.metadata.sdk.domain.enums.DeploymentMode;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicBoolean;

public class MetadataSdkContext {

    private static final AtomicBoolean INIT = new AtomicBoolean(false);
    private static MetadataSdkContext INSTANCE;

    private final String appCode;
    private final DeploymentMode deploymentMode;
    private final DatabaseType databaseType;
    private final boolean extensibilityEnabled;

    public MetadataSdkContext(MetadataSdkProperties props,
                              DataSourceProperties dsProps) {
        if (INIT.compareAndSet(false, true)) {
            this.appCode = props.getAppcode();
            this.deploymentMode = props.getDeploymentMode();
            this.extensibilityEnabled = StringUtils.hasText(appCode);

            this.databaseType = DatabaseType.fromJdbcUrl(dsProps.getUrl());
            INSTANCE = this;
        } else {
            // 已初始化：跳过重复设置
            this.appCode = INSTANCE.appCode;
            this.deploymentMode = INSTANCE.deploymentMode;
            this.databaseType = INSTANCE.databaseType;
            this.extensibilityEnabled = INSTANCE.extensibilityEnabled;
        }
    }

    private static void check() {
        if (INSTANCE == null) {
            throw new IllegalStateException(
                    "MetadataSdkContext 尚未初始化，请检查 Spring 配置");
        }
    }

    public static String getAppCode() {
        check();
        return INSTANCE.appCode;
    }

    public static DeploymentMode getDeploymentMode() {
        check();
        return INSTANCE.deploymentMode;
    }


    public static boolean isExtensibilityEnabled() {
        check();
        return INSTANCE.extensibilityEnabled;
    }

    public static DatabaseType getDatabaseType() {
        check();
        return INSTANCE.databaseType;
    }
}