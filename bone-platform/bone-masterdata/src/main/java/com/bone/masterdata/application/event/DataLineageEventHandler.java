package com.bone.masterdata.application.event;

import com.bone.core.tenant.context.TenantContextRunner;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.masterdata.domain.model.lineage.LineageRecord;
import com.bone.masterdata.domain.model.lineage.event.DataLineageEvent;
import com.bone.masterdata.domain.repository.LineageRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/** 血缘事件监听：将 {@link DataLineageEvent} 落库为 {@link LineageRecord}。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataLineageEventHandler {

  private final LineageRecordRepository lineageRecordRepository;

  /**
   * AFTER_COMMIT 阶段已脱离 HTTP 请求线程，{@code TenantContext} 通常为 null；而 SDK 写路径按 ADR-0029
   * <b>失败关闭</b>（缺租户直接抛），若不显式切租户，血缘落库会整条静默失效（仅留一行 warn，极难定位）。
   *
   * <p>事件携带 {@code tenantId}（由发布方 {@code LineageApplicationService} 在请求上下文内捕获），此处用 {@link
   * TenantContextRunner#runAs} 显式重建租户上下文后落库，与 blueprint 各 AFTER_COMMIT 处理器同范式。 判定用 {@code < 0}（外加
   * null）而非 {@code <= 0}：tenantId=0 是合法的平台租户。
   */
  @TransactionalEventListener
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(DataLineageEvent event) {
    Long tenantId = event.tenantId();
    if (tenantId == null || tenantId < 0) {
      log.warn(
          "血缘记录跳过：事件缺少有效 tenantId | source={} target={}",
          event.sourceEntity(),
          event.targetEntity());
      return;
    }
    TenantContextRunner.runAs(
        tenantId,
        () -> {
          Long id = DistributedIdGenerator.generateLongId();
          LineageRecord record =
              LineageRecord.create(
                  id,
                  tenantId,
                  event.sourceEntity(),
                  event.sourceField(),
                  event.transformType(),
                  event.targetEntity(),
                  event.targetField(),
                  event.schemaName());
          lineageRecordRepository.save(record);
          log.info(
              "血缘记录已保存: {}:{}=>{}:{} ({}: {})",
              event.sourceEntity(),
              event.sourceField(),
              event.targetEntity(),
              event.targetField(),
              event.transformType(),
              event.schemaName());
        });
  }
}
