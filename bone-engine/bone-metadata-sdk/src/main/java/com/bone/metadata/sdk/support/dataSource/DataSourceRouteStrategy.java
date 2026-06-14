package com.bone.metadata.sdk.support.dataSource;

/** 数据源路由策略接口 用于定义如何根据SQL或其他条件选择合适的数据源 */
@FunctionalInterface
public interface DataSourceRouteStrategy {

  /**
   * 选择数据源
   *
   * @param sql 执行的SQL语句
   * @return 选中的数据源名称
   */
  String chooseDataSource(String sql);
}
