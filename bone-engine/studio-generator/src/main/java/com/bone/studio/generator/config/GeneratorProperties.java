package com.bone.studio.generator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "bone.generator")
public class GeneratorProperties {

    @NestedConfigurationProperty
    private final LroConfig lro = new LroConfig();

    public LroConfig getLro() {
        return lro;
    }

    public static class LroConfig {
        /** 是否对 POST /code-generation 启用 LRO */
        private boolean codeGenerationEnabled = true;

        /**
         * true：默认同步 200；false：默认 202 + GET /operations/{id} 轮询。
         */
        private boolean codeGenerationSyncByDefault = false;

        public boolean isCodeGenerationEnabled() {
            return codeGenerationEnabled;
        }

        public void setCodeGenerationEnabled(boolean codeGenerationEnabled) {
            this.codeGenerationEnabled = codeGenerationEnabled;
        }

        public boolean isCodeGenerationSyncByDefault() {
            return codeGenerationSyncByDefault;
        }

        public void setCodeGenerationSyncByDefault(boolean codeGenerationSyncByDefault) {
            this.codeGenerationSyncByDefault = codeGenerationSyncByDefault;
        }
    }
}
