package com.bone.engine.extension;

import com.bone.engine.extension.register.ExtProviderRegister;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ExtPointAutoConfiguration
 *
 * @author renhui.trh 2023-11-1
 */
@Configuration
public class ExtPointAutoConfiguration {

    @Bean(initMethod = "init")
    public ExtProviderRegister extProviderRegister() {
        return new ExtProviderRegister();
    }
}
