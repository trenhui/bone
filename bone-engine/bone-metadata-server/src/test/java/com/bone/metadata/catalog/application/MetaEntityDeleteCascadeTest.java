package com.bone.metadata.catalog.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.metadata.catalog.domain.gateway.ActionDefMaterializePort;
import com.bone.metadata.catalog.domain.gateway.CurrentUserProvider;
import com.bone.metadata.catalog.domain.gateway.PhysicalStructureGateway;
import com.bone.metadata.catalog.domain.gateway.TenantProvider;
import com.bone.metadata.catalog.domain.model.meta.MetaEntity;
import com.bone.metadata.catalog.domain.repository.MetaEntityRelationRepository;
import com.bone.metadata.catalog.domain.repository.MetaEntityRepository;
import com.bone.metadata.catalog.domain.repository.MetaFieldRepository;
import com.bone.metadata.catalog.domain.service.IamModuleValidator;
import com.bone.metadata.sdk.metadata.api.MetadataService;
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
 * 删除实体的级联软删运行时动作定义单测。
 *
 * <p><b>背景</b>：删除实体此前只删 {@code meta_entity} 一行，{@code meta_entity_action_def} 原样存活。后果有两个——①
 * 运行时仍向一个已不存在的实体暴露动作入口（幽灵按钮 / 幽灵 API）；② 重建同名实体时 UPSERT 会把旧定义复活，用户看到「没配过却有动作」。
 *
 * <p><b>关键契约</b>：级联必须按<b>改写前</b>的原始 code 定位。草稿删除会追加 {@code _del_<id>} 后缀释放唯一键， 若按改写后的 code
 * 去删，将一条都匹配不到——那是「看似级联实则静默漏删」的假门禁，故用 {@link ArgumentCaptor} 断言参数值本身。
 */
@ExtendWith(MockitoExtension.class)
class MetaEntityDeleteCascadeTest {

  @Mock private MetaEntityRepository metaEntityRepository;
  @Mock private MetaFieldRepository metaFieldRepository;
  @Mock private MetaEntityRelationRepository metaEntityRelationRepository;
  @Mock private IamModuleValidator iamModuleValidator;
  @Mock private TenantProvider tenantProvider;
  @Mock private PhysicalStructureGateway physicalStructureGateway;
  @Mock private ActionDefMaterializePort actionDefMaterializePort;
  @Mock private DomainEventPublisher domainEventPublisher;

  private MetaEntityApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new MetaEntityApplicationService(
            metaEntityRepository,
            metaFieldRepository,
            metaEntityRelationRepository,
            iamModuleValidator,
            tenantProvider,
            physicalStructureGateway,
            org.mockito.Mockito.mock(MetadataService.class),
            (CurrentUserProvider) () -> null,
            Optional.empty(),
            actionDefMaterializePort,
            domainEventPublisher,
            org.mockito.Mockito.mock(PlatformTransactionManager.class),
            mock(UiSchemaApplicationService.class));
  }

  /** DRAFT 实体：create 默认 status=0（DRAFT），会触发唯一键改写路径。 */
  private static MetaEntity draft(long id, String code, long tenantId) {
    return MetaEntity.create(id, tenantId, "报销", code, "报销", null, "t_" + code, 0, 1, null, 1L);
  }

  @Test
  @DisplayName("删除实体：按原始 code 级联软删动作定义（草稿 code 被改写后仍命中）")
  void deleteEntity_shouldCascadeActionDefs_withOriginalCode() {
    MetaEntity entity = draft(9001L, "ec_verify_x", 7L);
    when(metaEntityRepository.findById(9001L)).thenReturn(entity);
    when(actionDefMaterializePort.deleteByEntity(7L, "ec_verify_x")).thenReturn(4);

    service.deleteEntity(9001L);

    // 级联参数必须是原始 code，而非 releaseUniqueKeysForDelete 追加 _del_<id> 后的值
    ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
    verify(actionDefMaterializePort).deleteByEntity(anyLong(), codeCaptor.capture());
    org.assertj.core.api.Assertions.assertThat(codeCaptor.getValue())
        .as("级联必须按原始 code 定位：草稿删除会把 code 改写为 ec_verify_x_del_9001，按改写值将静默漏删")
        .isEqualTo("ec_verify_x");
    verify(metaEntityRepository).deleteById(9001L);
  }

  @Test
  @DisplayName("负向：实体不存在时不得级联删除（否则会把别的实体动作定义误删）")
  void deleteEntity_missingEntity_shouldNotCascade() {
    when(metaEntityRepository.findById(anyLong())).thenReturn(null);

    assertThatThrownBy(() -> service.deleteEntity(404L))
        .isInstanceOf(com.bone.core.exception.BizException.class);

    verify(actionDefMaterializePort, never()).deleteByEntity(anyLong(), anyString());
    verify(metaEntityRepository, never()).deleteById(anyLong());
  }

  @Test
  @DisplayName("显式勾选连表删除：RUNTIME 实体按表名 DROP（补上删除闭环，治 36 张无主表）")
  void deleteEntity_dropPhysicalTable_runtime_shouldDropTable() {
    MetaEntity entity = draft(9001L, "ec_verify_x", 7L); // draft() 默认 RUNTIME(1)
    when(metaEntityRepository.findById(9001L)).thenReturn(entity);
    when(physicalStructureGateway.dropTable("t_ec_verify_x"))
        .thenReturn(
            com.bone.metadata.catalog.domain.model.physical.PhysicalStructurePlan.of(
                null,
                "t_ec_verify_x",
                List.of("DROP TABLE IF EXISTS `t_ec_verify_x`"),
                1,
                com.bone.metadata.catalog.domain.model.physical.PhysicalStructurePlan
                    .STATUS_DROPPED,
                "已删除物理表"));

    service.deleteEntity(9001L, true);

    verify(physicalStructureGateway).dropTable("t_ec_verify_x");
    verify(metaEntityRepository).deleteById(9001L);
  }

  @Test
  @DisplayName("负向：GENERATIVE 实体勾选连表删除也不得调网关（从未建表，避免无意义 DDL）")
  void deleteEntity_dropPhysicalTable_generative_shouldNotDropTable() {
    MetaEntity entity =
        MetaEntity.create(9002L, 7L, "报销", "ec_gen_x", "报销", null, "t_ec_gen_x", 0, 0, null, 1L);
    when(metaEntityRepository.findById(9002L)).thenReturn(entity);

    service.deleteEntity(9002L, true);

    verify(physicalStructureGateway, never()).dropTable(anyString());
    verify(metaEntityRepository).deleteById(9002L);
  }

  @Test
  @DisplayName("负向：未勾选连表删除（默认）不得 DROP——软删语义不变，历史数据保留")
  void deleteEntity_defaultFlag_shouldNotDropTable() {
    MetaEntity entity = draft(9003L, "ec_verify_y", 7L);
    when(metaEntityRepository.findById(9003L)).thenReturn(entity);

    service.deleteEntity(9003L, false);

    verify(physicalStructureGateway, never()).dropTable(anyString());
  }
}
