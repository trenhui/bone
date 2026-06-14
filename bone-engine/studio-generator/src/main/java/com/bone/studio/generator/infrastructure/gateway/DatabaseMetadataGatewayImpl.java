package com.bone.studio.generator.infrastructure.gateway;

import com.bone.core.exception.InfrastructureException;
import com.bone.studio.generator.common.StudioIds;
import com.bone.studio.generator.domain.data.DataSource;
import com.bone.studio.generator.domain.data.DatabaseTable;
import com.bone.studio.generator.domain.data.TableColumn;
import com.bone.studio.generator.domain.gateway.DatabaseMetadataGateway;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseMetadataGatewayImpl implements DatabaseMetadataGateway {

  private final ConcurrentHashMap<String, HikariDataSource> poolCache = new ConcurrentHashMap<>();

  @Value("${generator.encryption.key:default-key}")
  private String encKey;

  @Override
  public boolean testConnection(DataSource dataSource) {
    try (HikariDataSource ds = buildTempPool(dataSource)) {
      try (Connection conn = ds.getConnection()) {
        return conn.isValid(5);
      }
    } catch (Exception e) {
      log.error("测试数据库连接失败, dataSource={}, error={}", dataSource, e.getMessage(), e);
      return false;
    }
  }

  @Override
  public List<DatabaseTable> loadTables(DataSource dataSource) {
    HikariDataSource ds = getOrCreatePool(dataSource);
    try (Connection conn = ds.getConnection()) {
      DatabaseMetaData metaData = conn.getMetaData();
      ResultSet rs =
          metaData.getTables(dataSource.getDatabase(), null, "%", new String[] {"TABLE"});
      List<DatabaseTable> tables = new ArrayList<>();
      while (rs.next()) {
        String tableName = rs.getString("TABLE_NAME");
        String comment = rs.getString("REMARKS");
        List<TableColumn> columns = loadTableColumns(dataSource, tableName);
        tables.add(
            DatabaseTable.builder()
                .tableName(tableName)
                .tableComment(comment)
                .columns(columns)
                .build());
      }
      return tables;
    } catch (SQLException e) {
      throw new InfrastructureException("Failed to load tables from data source", e);
    }
  }

  @Override
  public List<TableColumn> loadTableColumns(DataSource dataSource, String tableName) {
    HikariDataSource ds = getOrCreatePool(dataSource);
    try (Connection conn = ds.getConnection()) {
      DatabaseMetaData metaData = conn.getMetaData();
      ResultSet rs = metaData.getColumns(dataSource.getDatabase(), null, tableName, null);
      List<TableColumn> columns = new ArrayList<>();
      while (rs.next()) {
        columns.add(
            TableColumn.builder()
                .columnName(rs.getString("COLUMN_NAME"))
                .jdbcType(rs.getInt("DATA_TYPE"))
                .columnType(rs.getString("TYPE_NAME"))
                .columnSize(rs.getInt("COLUMN_SIZE"))
                .decimalDigits(rs.getInt("DECIMAL_DIGITS"))
                .nullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable)
                .remarks(rs.getString("REMARKS"))
                .build());
      }
      return columns;
    } catch (SQLException e) {
      throw new InfrastructureException("Failed to load columns for table " + tableName, e);
    }
  }

  private HikariDataSource buildTempPool(DataSource dataSource) {
    HikariConfig hc = new HikariConfig();
    hc.setJdbcUrl(buildUrl(dataSource));
    hc.setUsername(dataSource.getUsername());
    hc.setPassword(dataSource.getPassword());
    hc.setMaximumPoolSize(2);
    hc.setConnectionTimeout(5000);
    return new HikariDataSource(hc);
  }

  private HikariDataSource getOrCreatePool(DataSource dataSource) {
    return poolCache.computeIfAbsent(
        StudioIds.dataSourceKey(dataSource.getId()),
        id -> {
          HikariConfig hc = new HikariConfig();
          hc.setJdbcUrl(buildUrl(dataSource));
          hc.setUsername(dataSource.getUsername());
          hc.setPassword(dataSource.getPassword());
          hc.setMaximumPoolSize(5);
          hc.setIdleTimeout(600000);
          hc.setMaxLifetime(1800000);
          return new HikariDataSource(hc);
        });
  }

  private String buildUrl(DataSource dataSource) {
    String host = dataSource.getHost();
    int port = dataSource.getPort();
    String dbName = dataSource.getDatabase();
    switch (dataSource.getType()) {
      case "mysql":
        return String.format(
            "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&characterEncoding=utf8&useUnicode=true",
            host, port, dbName);
      case "postgresql":
        return String.format("jdbc:postgresql://%s:%d/%s", host, port, dbName);
      case "oracle":
        return String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, dbName);
      case "sqlserver":
        return String.format("jdbc:sqlserver://%s:%d;databaseName=%s", host, port, dbName);
      default:
        throw new IllegalArgumentException("Unsupported database type: " + dataSource.getType());
    }
  }
}
