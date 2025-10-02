package com.bone.metadata.sdk.support.tenant;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

public class TenantContextHolder {
    private static final InheritableThreadLocal<String> currentTenant = new InheritableThreadLocal<>();
    private static final AbstractRoutingDataSource dynamicDataSource = new AbstractRoutingDataSource() {
        @Override
        protected Object determineCurrentLookupKey() {
            return currentTenant.get();
        }
    };

    public static void setTenantId(String tenantId) {
        currentTenant.set(tenantId);
        dynamicDataSource.afterPropertiesSet(); // 刷新数据源
    }

    public static String getCurrentTenant() {
        return currentTenant.get();
    }

    public static void registerDataSource(String tenantId, DataSource dataSource) {
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put(tenantId, dataSource);
        dynamicDataSource.setTargetDataSources(targetDataSources);
    }
}