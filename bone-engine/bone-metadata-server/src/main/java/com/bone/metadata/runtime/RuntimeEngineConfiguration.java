package com.bone.metadata.runtime;

import com.bone.metadata.engine.runtime.JdbcRuntimeRecordService;
import com.bone.metadata.engine.runtime.RuntimeEntityCatalog;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
public class RuntimeEngineConfiguration {

  @Bean
  public JdbcRuntimeRecordService jdbcRuntimeRecordService(
      NamedParameterJdbcTemplate jdbcTemplate, RuntimeEntityCatalog runtimeEntityCatalog) {
    return new JdbcRuntimeRecordService(jdbcTemplate, runtimeEntityCatalog);
  }
}
