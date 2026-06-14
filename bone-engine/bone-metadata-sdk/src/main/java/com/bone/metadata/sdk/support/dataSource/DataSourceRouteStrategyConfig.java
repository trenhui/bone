package com.bone.metadata.sdk.support.dataSource;

import com.bone.metadata.sdk.support.util.SqlUtil;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/** 自定义数据源路由策略配置 */
@Configuration
public class DataSourceRouteStrategyConfig {

  private static final Logger log = LoggerFactory.getLogger(DataSourceRouteStrategyConfig.class);

  /** 从库负载均衡策略接口 */
  public interface DsLoadBalanceAlgorithm {
    String choose(List<String> dsNames, String sql);
  }

  /** 从库负载均衡策略 - 轮询 适用于多个从库性能相近的场景 */
  @Bean
  public DsLoadBalanceAlgorithm roundRobinLoadBalance() {
    return new DsLoadBalanceAlgorithm() {
      private final AtomicInteger counter = new AtomicInteger(0);

      @Override
      public String choose(List<String> dsNames, String sql) {
        if (dsNames == null || dsNames.isEmpty()) {
          log.warn("没有可用的数据源进行负载均衡");
          return null;
        }

        int index = Math.abs(counter.getAndIncrement() % dsNames.size());
        String chosenDs = dsNames.get(index);
        log.debug("轮询负载均衡选择的数据源: {} (索引: {})", chosenDs, index);
        return chosenDs;
      }
    };
  }

  /** 从库负载均衡策略 - 随机 适用于需要随机分发请求的场景 */
  @Bean
  public DsLoadBalanceAlgorithm randomLoadBalance() {
    return new DsLoadBalanceAlgorithm() {
      private final Random random = new Random();

      @Override
      public String choose(List<String> dsNames, String sql) {
        if (dsNames == null || dsNames.isEmpty()) {
          log.warn("没有可用的数据源进行负载均衡");
          return null;
        }

        int index = random.nextInt(dsNames.size());
        String chosenDs = dsNames.get(index);
        log.debug("随机负载均衡选择的数据源: {} (索引: {})", chosenDs, index);
        return chosenDs;
      }
    };
  }

  /** 数据源路由策略接口 */
  public interface RoutingStrategy {
    /**
     * 根据SQL语句选择合适的数据源
     *
     * @param sql SQL语句
     * @param availableDataSources 可用的数据源列表
     * @return 选择的数据源名称
     */
    String route(String sql, List<String> availableDataSources);
  }

  /** 基于SQL类型的数据源路由策略 根据SQL是读操作还是写操作来选择数据源 */
  @Bean
  public RoutingStrategy sqlTypeRoutingStrategy() {
    return new RoutingStrategy() {
      @Override
      public String route(String sql, List<String> availableDataSources) {
        if (!StringUtils.hasText(sql)) {
          log.warn("空SQL无法进行路由，使用默认数据源");
          return "master"; // 默认使用主库
        }

        // 转换SQL为大写进行比较
        String upperSql = sql.trim().toUpperCase();

        // 检查是否为写操作SQL
        if (isWriteOperation(upperSql)) {
          log.debug("SQL已识别为写操作，路由到主数据源");
          return "master";
        } else {
          // 读操作，使用第一个可用的从库，如果没有从库则使用主库
          String slaveDs = getAvailableSlaveDataSource(availableDataSources);
          log.debug("SQL已识别为读操作，路由到: {}", slaveDs);
          return slaveDs;
        }
      }

      /**
       * 判断SQL是否为写操作
       *
       * @param upperSql SQL字符串
       * @return 是否为写操作
       */
      private boolean isWriteOperation(String upperSql) {
        return SqlUtil.isWriteOperation(upperSql);
      }

      /**
       * 获取可用的从库数据源
       *
       * @param availableDataSources 可用数据源列表
       * @return 从库数据源名称，如果没有则返回master
       */
      private String getAvailableSlaveDataSource(List<String> availableDataSources) {
        if (availableDataSources == null || availableDataSources.isEmpty()) {
          return "master";
        }

        // 尝试找到名称包含slave的数据源
        for (String ds : availableDataSources) {
          if (ds.toLowerCase().contains("slave")) {
            return ds;
          }
        }

        // 如果没有找到从库，返回master
        return "master";
      }
    };
  }
}
