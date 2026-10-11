package com.bone.metadata.runtime;

import com.bone.metadata.engine.runtime.JdbcRuntimeRecordService;
import com.bone.metadata.engine.runtime.RuntimeAuditSink;
import com.bone.metadata.engine.runtime.RuntimeEntityCatalog;
import com.bone.metadata.engine.runtime.RuntimeRelationSupport;
import com.bone.metadata.engine.runtime.RuntimeStateEntryResolver;
import com.bone.metadata.engine.runtime.RuntimeWriteHook;
import com.bone.metadata.runtime.application.RuntimeStatusGuard;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@Configuration
public class RuntimeEngineConfiguration {

  /**
   * 审计 sink 接入写路径（RC-009）。
   *
   * <p>此前 {@code DefaultAuditService} 已存在但<strong>未接入</strong> metadata-server 的运行时写路径——
   * 状态字段改了却无人留痕。此处把 sink 注入 engine，使受保护字段变更 / 软删 / 恢复在写路径内落审计， 而不是靠调用方自觉。
   */
  @Bean
  public JdbcRuntimeRecordService jdbcRuntimeRecordService(
      NamedParameterJdbcTemplate jdbcTemplate,
      RuntimeEntityCatalog runtimeEntityCatalog,
      RuntimeAuditSink auditSink,
      RuntimeStateEntryResolver stateEntryResolver,
      RuntimeRelationSupport runtimeRelationSupport,
      List<RuntimeWriteHook> writeHooks) {
    return new JdbcRuntimeRecordService(
        jdbcTemplate,
        runtimeEntityCatalog,
        auditSink,
        stateEntryResolver,
        runtimeRelationSupport,
        writeHooks);
  }

  /**
   * G10 关系能力（关联lookup / 子表 / 附件）：引用存在性校验 + 显示值批量补全 + 子表级联。
   *
   * <p>与 {@code JdbcRuntimeRecordService} 同源同连接（bone-metadata-sdk JDBC），避免第二条数据通道。
   */
  @Bean
  public RuntimeRelationSupport runtimeRelationSupport(
      NamedParameterJdbcTemplate jdbcTemplate, RuntimeEntityCatalog runtimeEntityCatalog) {
    return new RuntimeRelationSupport(jdbcTemplate, runtimeEntityCatalog);
  }

  /**
   * 状态机入口状态解析：复用 {@link RuntimeStatusGuard} 的转移矩阵推导（{@code from − to} 的无入边者）。
   *
   * <p>同一个转移矩阵只推导一次：Controller 侧守卫用它做「快速失败」判断， 写路径用它注入入口状态 —— 二者读的是同一份动作定义，不存在第二套入口状态真源。
   */
  @Bean
  public RuntimeStateEntryResolver runtimeStateEntryResolver(RuntimeStatusGuard statusGuard) {
    // 注意参数序：resolver 约定 (entityCode, tenantId)，guard 暴露 (tenantId, entityCode)
    return (entityCode, tenantId) -> statusGuard.entryStatus(tenantId, entityCode);
  }
}
