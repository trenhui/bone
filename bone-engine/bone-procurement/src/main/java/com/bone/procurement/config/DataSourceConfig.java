package com.bone.procurement.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * 简化的数据源配置
 */
@Configuration
public class DataSourceConfig {

    private final Environment environment;

    public DataSourceConfig(Environment environment) {
        this.environment = environment;
    }

    /**
     * 简化的数据源配置
     */
    @Bean
    public DataSource dataSource() {
        // 使用一个简单的数据源实现，避免所有外部依赖
        return new DummyDataSource();
    }
    
    /**
     * 内部虚拟数据源类，避免外部依赖
     */
    private static class DummyDataSource implements DataSource {
        @Override
        public java.sql.Connection getConnection() {
            return null;
        }
        
        @Override
        public java.sql.Connection getConnection(String username, String password) {
            return null;
        }
        
        @Override
        public java.io.PrintWriter getLogWriter() {
            return null;
        }
        
        @Override
        public void setLogWriter(java.io.PrintWriter out) {
        }
        
        @Override
        public void setLoginTimeout(int seconds) {
        }
        
        @Override
        public int getLoginTimeout() {
            return 0;
        }
        
        @Override
        public java.util.logging.Logger getParentLogger() {
            return null;
        }
        
        @Override
        public <T> T unwrap(Class<T> iface) {
            return null;
        }
        
        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }
}