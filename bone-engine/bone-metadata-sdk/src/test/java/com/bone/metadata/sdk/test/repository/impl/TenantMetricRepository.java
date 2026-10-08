package com.bone.metadata.sdk.test.repository.impl;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.test.domain.TenantMetric;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TenantMetricRepository extends BaseRepository<TenantMetric, Long> {

  @Autowired
  public TenantMetricRepository(
      SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
    super(sqlBuilder, sqlExecutor, TenantMetric.class, extensionCoordinator);
  }
}
