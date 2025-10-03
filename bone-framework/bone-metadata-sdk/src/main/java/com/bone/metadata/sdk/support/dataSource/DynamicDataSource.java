package com.bone.metadata.sdk.support.dataSource;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class DynamicDataSource extends AbstractRoutingDataSource {
    @Override
    protected Object determineCurrentLookupKey() {
        return null; //DataSourceContextHolder.getDataSource();
    }
}
