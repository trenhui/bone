package com.bone.metadata.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.application.command.cmd.BatchPublishMetaEntityCommand;
import com.bone.metadata.catalog.common.BatchOperateResult;
import com.bone.metadata.catalog.domain.gateway.PhysicalStructureGateway;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.model.meta.event.MetaEntityPublishedEvent;
import com.bone.metadata.catalog.domain.model.physical.PhysicalTableSnapshot;
import com.bone.metadata.catalog.domain.repository.MetaEntityRelationRepository;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import com.bone.metadata.catalog.domain.service.IamModuleValidator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 发布领域事件（doc2a §337 步骤⑤，F2）单测：发布成功发事件、载荷正确；align 失败 / 实体不存在不发事件； 批量发布逐实体发事件（部分成功语义）。纯 Mockito，不依赖
 * Spring 上下文。
 */
@ExtendWith(MockitoExtension.class)
class MetaEntityPublishEventTest {

  @Mock private MetaEntityRepository metaEntityRepository;
  @Mock private MetaFieldRepository metaFieldRepository;
  @Mock private MetaEntityRelationRepository metaEntityRelationRepository;
  @Mock private IamModuleValidator iamModuleValidator;
  @Mock private TenantProvider tenantProvider;
  @Mock private PhysicalStructureGateway physicalStructureGateway;
  @Mock private DomainEventPublisher domainEventPublisher;

  private MetaEntityApplicationService service;

  @BeforeEach
  void setUp() {
    // 发布路径（RUNTIME）会经 readTableSnapshot 取物理表快照以分配机制 B 预留列；
    // 事件/异常翻译类测试不验证 B 分配，统一返回「表不存在」快照让 allocateReservedColumns 早退。
    lenient()
        .when(physicalStructureGateway.readTableSnapshot(anyString()))
        .thenReturn(new PhysicalTableSnapshot("placeholder", false, List.of()));
    service =
        new MetaEntityApplicationService(
            metaEntityRepository,
            metaFieldRepository,
            metaEntityRelationRepository,
            iamModuleValidator,
            tenantProvider,
            physicalStructureGateway,
            mock(com.bone.metadata.sdk.metadata.api.MetadataService.class),
            () -> null,
            Optional.empty(),
            domainEventPublisher,
            mock(PlatformTransactionManager.class));
  }

  @Test
  @DisplayName("GENERATIVE 发布成功 → 发一次事件，载荷（id/租户/编码/表名/模式/版本）正确")
  void publish_shouldEmitEvent_forGenerative() {
    entity(1L, "pub_gen", "t_pub_gen", 0);

    Integer version = service.publishEntity(1L, null);

    assertThat(version).isEqualTo(1);
    ArgumentCaptor<MetaEntityPublishedEvent> captor =
        ArgumentCaptor.forClass(MetaEntityPublishedEvent.class);
    verify(domainEventPublisher).publish(captor.capture());
    MetaEntityPublishedEvent event = captor.getValue();
    assertThat(event.entityId()).isEqualTo(1L);
    assertThat(event.tenantId()).isEqualTo(1L);
    assertThat(event.entityCode()).isEqualTo("pub_gen");
    assertThat(event.tableName()).isEqualTo("t_pub_gen");
    assertThat(event.deliveryMode()).isZero();
    assertThat(event.version()).isEqualTo(1);
    assertThat(event.eventTime()).isNotNull();
  }

  @Test
  @DisplayName("RUNTIME 发布成功（validate+align 通过）→ 发一次事件")
  void publish_shouldEmitEvent_forRuntimeAfterAlign() {
    entity(2L, "pub_rt", "t_pub_rt", 1);

    service.publishEntity(2L, null);

    verify(physicalStructureGateway).validateForPublish(1L, "pub_rt");
    verify(physicalStructureGateway).align(1L, "pub_rt");
    verify(domainEventPublisher).publish(any(MetaEntityPublishedEvent.class));
  }

  @Test
  @DisplayName("RUNTIME 物理漂移发布被拒 → 不发事件，异常上抛（发布失败无事件）")
  void publish_shouldNotEmitEvent_whenAlignRejected() {
    entity(3L, "pub_drift", "t_pub_drift", 1);
    doThrow(BizException.of(409, "检测到结构漂移：列 amount 类型不兼容"))
        .when(physicalStructureGateway)
        .validateForPublish(1L, "pub_drift");

    assertThatThrownBy(() -> service.publishEntity(3L, null)).isInstanceOf(BizException.class);

    verify(domainEventPublisher, never()).publish(any());
  }

  @Test
  @DisplayName("RUNTIME 物理漂移（DomainException）→ 应用层翻译为 409+META_DOMAIN_ERROR，不发事件")
  void publish_shouldTranslateDomainExceptionTo409() {
    entity(4L, "pub_drift2", "t_pub_drift2", 1);
    doThrow(
            new com.bone.core.exception.DomainException(
                "字段 amount 模型类型 DECIMAL 与物理列类型 VARCHAR 不兼容"))
        .when(physicalStructureGateway)
        .validateForPublish(1L, "pub_drift2");

    assertThatThrownBy(() -> service.publishEntity(4L, null))
        .isInstanceOf(BizException.class)
        .satisfies(
            e -> {
              BizException biz = (BizException) e;
              org.junit.jupiter.api.Assertions.assertEquals(409, biz.getCode());
              org.junit.jupiter.api.Assertions.assertEquals(
                  com.bone.metadata.catalog.common.CatalogErrorCodes.META_DOMAIN_ERROR,
                  biz.getErrorCode());
            });

    verify(domainEventPublisher, never()).publish(any());
  }

  @Test
  @DisplayName("实体不存在 → 抛 BizException 且不发事件")
  void publish_shouldNotEmitEvent_whenEntityMissing() {
    when(metaEntityRepository.findById(404L)).thenReturn(null);

    assertThatThrownBy(() -> service.publishEntity(404L, null))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("实体不存在");

    verify(domainEventPublisher, never()).publish(any());
  }

  @Test
  @DisplayName("批量发布部分成功 → 成功者逐个发事件，失败者无事件")
  void batchPublish_shouldEmitEventPerSuccess() {
    MetaEntity ok1 = entity(11L, "pub_b1", "t_pub_b1", 0);
    MetaEntity ok2 = entity(13L, "pub_b3", "t_pub_b3", 0);
    when(metaEntityRepository.findById(12L)).thenReturn(null);

    BatchPublishMetaEntityCommand cmd = new BatchPublishMetaEntityCommand();
    cmd.setIds(List.of(11L, 12L, 13L));
    BatchOperateResult result = service.batchPublishEntities(cmd);

    assertThat(result.getSuccessCount()).isEqualTo(2);
    assertThat(result.getFailCount()).isEqualTo(1);
    verify(domainEventPublisher, times(2)).publish(any(MetaEntityPublishedEvent.class));
    ArgumentCaptor<MetaEntityPublishedEvent> captor =
        ArgumentCaptor.forClass(MetaEntityPublishedEvent.class);
    verify(domainEventPublisher, times(2)).publish(captor.capture());
    assertThat(captor.getAllValues())
        .extracting(MetaEntityPublishedEvent::entityId)
        .containsExactly(ok1.getId(), ok2.getId());
    // 部分成功回执含失败原因
    assertThat(result.getErrors()).anyMatch(e -> e.contains("id=12"));
  }

  // ===================== 辅助 =====================

  /** 建一个草稿实体（status=DRAFT、version=0），发布后 bumpVersion → 1。 */
  private MetaEntity entity(long id, String code, String tableName, int deliveryMode) {
    MetaEntity e =
        MetaEntity.create(null, 1L, code, code, code, null, tableName, 0, deliveryMode, null, 7L);
    e.setId(id);
    when(metaEntityRepository.findById(id)).thenReturn(e);
    return e;
  }
}
