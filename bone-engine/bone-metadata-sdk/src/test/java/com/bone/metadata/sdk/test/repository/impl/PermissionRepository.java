package com.bone.metadata.sdk.test.repository.impl;

import com.bone.metadata.sdk.BaseRepository;
import com.bone.metadata.sdk.extension.ExtensionCoordinator;
import com.bone.metadata.sdk.query.SqlBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import com.bone.metadata.sdk.test.domain.Permission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PermissionRepository extends BaseRepository<Permission, Long> {
  /**
   * 构造函数，初始化数据库操作相关组件。
   *
   * @param sqlBuilder SQL 构建引擎，用于生成查询语句
   * @param sqlExecutor SQL 查询执行器，处理命名查询
   */
  @Autowired
  public PermissionRepository(
      SqlBuilder sqlBuilder, SqlExecutor sqlExecutor, ExtensionCoordinator extensionCoordinator) {
    super(sqlBuilder, sqlExecutor, Permission.class, extensionCoordinator);
  }
}
