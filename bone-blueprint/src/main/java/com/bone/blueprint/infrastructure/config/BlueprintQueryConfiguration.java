package com.bone.blueprint.infrastructure.config;

import com.bone.metadata.sdk.query.dsl.QueryBuilder;
import com.bone.metadata.sdk.sql.executor.SqlExecutor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class BlueprintQueryConfiguration {

  private final SqlExecutor sqlExecutor;

  @PostConstruct
  public void initQueryBuilder() {
    QueryBuilder.initialize(sqlExecutor);
  }
}
