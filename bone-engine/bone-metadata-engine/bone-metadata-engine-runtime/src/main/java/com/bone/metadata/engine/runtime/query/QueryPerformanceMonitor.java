package com.bone.metadata.engine.runtime.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 查询性能监控器，用于记录和监控查询执行性能 */
@Component
public class QueryPerformanceMonitor {

  // 手动添加log变量，因为@Slf4j注解可能没有正确工作
  private static final Logger LOGGER = LoggerFactory.getLogger(QueryPerformanceMonitor.class);

  // 慢查询阈值（毫秒）
  private static final long SLOW_QUERY_THRESHOLD = 1000;

  /**
   * 记录查询指标
   *
   * @param context 查询执行上下文
   */
  public void recordQueryMetrics(QueryExecutionContext context) {
    long executionTime = calculateExecutionTime(context);

    // 构建性能指标日志
    StringBuilder metricsBuilder = new StringBuilder();
    metricsBuilder.append("查询性能指标: ");
    metricsBuilder.append("用户ID=").append(context.getUserId()).append(", ");
    metricsBuilder.append("执行时间=").append(executionTime).append("ms, ");
    metricsBuilder.append("结果数量=").append(context.getResultCount()).append(", ");
    metricsBuilder.append("缓存命中=").append(context.isCacheHit()).append(", ");
    metricsBuilder.append("成功=").append(context.isSuccess());

    if (!context.isSuccess() && context.getErrorMessage() != null) {
      metricsBuilder.append(", 错误=").append(context.getErrorMessage());
    }

    // 记录日志
    if (executionTime > SLOW_QUERY_THRESHOLD) {
      // 慢查询记录为警告级别
      LOGGER.warn("{}", metricsBuilder.toString());
      LOGGER.warn("Slow query SQL: {}", context.getGeneratedSql());
    } else {
      // 普通查询记录为调试级别
      LOGGER.debug("{}", metricsBuilder.toString());
    }

    // 这里可以添加将性能指标保存到数据库或监控系统的逻辑
    // 例如：savePerformanceMetrics(context, executionTime);
  }

  /**
   * 计算查询执行时间
   *
   * @param context 查询执行上下文
   * @return 执行时间（毫秒）
   */
  private long calculateExecutionTime(QueryExecutionContext context) {
    return System.currentTimeMillis() - context.getStartTime();
  }

  /** 获取慢查询阈值 */
  public long getSlowQueryThreshold() {
    return SLOW_QUERY_THRESHOLD;
  }
}
