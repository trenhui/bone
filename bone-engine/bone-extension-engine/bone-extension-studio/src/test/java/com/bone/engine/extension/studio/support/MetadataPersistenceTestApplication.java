package com.bone.engine.extension.studio.support;

import com.bone.engine.extension.studio.config.StudioMetadataPersistenceConfiguration;
import com.bone.metadata.sdk.support.config.MetadataAutoConfiguration;
import com.bone.metadata.sdk.support.config.SqlRepositoryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * 集成测试专用启动类：仅加载 Metadata 仓储与 Studio Store，不启动扩展引擎运行时与安全。
 */
@SpringBootApplication(
        exclude = {
            SecurityAutoConfiguration.class,
            ManagementWebSecurityAutoConfiguration.class,
            MetadataAutoConfiguration.class
        })
@ComponentScan(
        basePackages = {
            "com.bone.engine.extension.studio.infrastructure.persistence",
            "com.bone.metadata.sdk.extension"
        })
@Import({
    SqlRepositoryAutoConfiguration.class,
    StudioMetadataPersistenceConfiguration.class,
    MetadataPersistenceTestSupport.class
})
public class MetadataPersistenceTestApplication {}
