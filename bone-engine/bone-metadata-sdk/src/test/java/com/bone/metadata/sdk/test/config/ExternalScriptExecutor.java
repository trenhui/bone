package com.bone.metadata.sdk.test.config;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

@Component
public class ExternalScriptExecutor {

  private final DataSource dataSource;
  private final Environment environment;

  @Autowired
  public ExternalScriptExecutor(DataSource dataSource, Environment environment) {
    this.dataSource = dataSource;
    this.environment = environment;
  }

  @PostConstruct
  public void executeScripts() {
    String jdbcUrl = environment.getProperty("spring.datasource.url", "").toLowerCase(Locale.ROOT);

    if (jdbcUrl.contains("mysql:")) {
      runScripts("mysql");
    } else if (jdbcUrl.contains("oracle:")) {
      runScripts("oracle");
    } else if (jdbcUrl.contains("postgresql:")) {
      runScripts("postgresql");
    } else if (jdbcUrl.contains("sqlserver:")) {
      runScripts("sqlserver");
    } else if (jdbcUrl.contains("h2:")) {
      runScripts("h2");
    } else {
      System.out.println("未识别的数据源类型，跳过 SQL 脚本执行。");
    }
  }

  private void runScripts(String dbType) {
    String[] scriptPaths = {
      String.format("sql/%s/schema.sql", dbType),
      String.format("sql/%s/ext_schema.sql", dbType),
      String.format("sql/%s/data.sql", dbType)
    };

    for (String path : scriptPaths) {
      try (Connection conn = dataSource.getConnection()) {
        Resource resource = new ClassPathResource(path);
        if (resource.exists()) {
          ScriptUtils.executeSqlScript(conn, resource);
        } else {
          System.out.printf("脚本文件不存在: %s，跳过执行%n", path);
        }
      } catch (Exception e) {
        throw new RuntimeException("执行 SQL 脚本失败: " + path, e);
      }
    }
  }
}
