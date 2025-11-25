package com.bone.example.extension.config;

import com.bone.engine.extension.api.annotation.EnableExtensionPoints;
import com.bone.engine.extension.support.config.ExtensionAutoConfiguration;
import com.bone.engine.extension.support.config.ExtensionProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@EnableAutoConfiguration
@ImportAutoConfiguration({
        ExtensionAutoConfiguration.class
})
@ComponentScan({"com.bone.example.extension", "com.bone.engine.extension"})
@EnableConfigurationProperties(ExtensionProperties.class)
@EnableExtensionPoints
public class TestConfig {

    @Autowired
    private Environment environment;

}