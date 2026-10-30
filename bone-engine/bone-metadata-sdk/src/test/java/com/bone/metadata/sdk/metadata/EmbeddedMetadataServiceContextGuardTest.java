package com.bone.metadata.sdk.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.metadata.sdk.domain.model.AllocationContext;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.extension.ColumnAllocator;
import com.bone.metadata.sdk.extension.repository.FieldMetadataRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code findExtensionFieldsByNames} 的上下文契约测试。
 *
 * <p><b>真实事故</b>：{@code POST /api/v1/metadata/fields:searchByNames} 只传 {@code logicalNames} 而不传
 * {@code context} ⇒ 下游拼缓存键时 {@code ctx.getTenantId().toString()} 当场 NPE ⇒ 对外表现为 <b>500
 * COMMON_INTERNAL_ERROR</b>。
 *
 * <p><b>为什么历史巡检没抓到</b>：该方法的第一行是 {@code logicalNames 为空 → 返回空列表}， 恰好在触及 ctx 之前就返回了。而此前所有自动化探针都是「空
 * body」打法（names 为 null）， 永远走这条提前返回分支 ⇒ 500 被结构性掩盖，巡检报告长期显示「0 真实 5xx」。 因此本测试必须<b>显式构造 names 非空 +
 * context 残缺</b>的组合，才能真正走到出事的那一行。
 */
class EmbeddedMetadataServiceContextGuardTest {

  private FieldMetadataRepository repository;
  private EmbeddedMetadataService service;

  @BeforeEach
  void setUp() {
    repository = mock(FieldMetadataRepository.class);
    service = new EmbeddedMetadataService(mock(ColumnAllocator.class), repository);
  }

  @Test
  @DisplayName("names 非空 + context 为 null → 契约错误，绝不能是 NPE/500")
  void nullContextWithNamesThrowsContractError() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> service.findExtensionFieldsByNames(null, List.of("ext_field_a")),
            "缺 context 必须以明确的契约错误失败，而不是让空指针冒泡成 500");
    assertTrue(
        ex.getMessage().contains("tenantId"), () -> "错误信息应指明缺少哪个键，便于调用方自检: " + ex.getMessage());
    verify(repository, never()).findByContextAndNames(any(), anyList());
  }

  @Test
  @DisplayName("context 缺 tenantId → 契约错误（缓存键与查询分区都依赖它）")
  void missingTenantIdThrowsContractError() {
    AllocationContext ctx = new AllocationContext(null, "bone-iam", "bone", "account");
    assertThrows(
        IllegalArgumentException.class,
        () -> service.findExtensionFieldsByNames(ctx, List.of("ext_field_a")));
    verify(repository, never()).findByContextAndNames(any(), anyList());
  }

  @Test
  @DisplayName("context 缺 appCode/entityType → 契约错误（否则缓存键退化成 null.null，跨实体串缓存）")
  void missingRoutingKeysThrowContractError() {
    AllocationContext noAppCode = new AllocationContext(1L, null, "bone", "account");
    assertThrows(
        IllegalArgumentException.class,
        () -> service.findExtensionFieldsByNames(noAppCode, List.of("ext_field_a")));

    AllocationContext noEntityType = new AllocationContext(1L, "bone-iam", "bone", null);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.findExtensionFieldsByNames(noEntityType, List.of("ext_field_a")));
  }

  @Test
  @DisplayName("names 为空仍短路返回空列表（保住既有 200 语义，不被新校验误伤）")
  void emptyNamesStillShortCircuitsBeforeContextCheck() {
    assertTrue(
        service.findExtensionFieldsByNames(null, List.of()).isEmpty(),
        "空 names 必须在触达 context 校验前短路——这是既有对外契约，不得因为补校验而改变");
    assertTrue(service.findExtensionFieldsByNames(null, null).isEmpty());
    verify(repository, never()).findByContextAndNames(any(), anyList());
  }

  @Test
  @DisplayName("完整上下文 → 正常委派仓储（正常路径未被守卫打断）")
  void completeContextDelegatesToRepository() {
    AllocationContext ctx = AllocationContext.of(1L, "bone-iam", "bone", "account");
    when(repository.findByContextAndNames(ctx, List.of("ext_field_a")))
        .thenReturn(List.of(new FieldMetadata()));

    List<FieldMetadata> result = service.findExtensionFieldsByNames(ctx, List.of("ext_field_a"));

    assertEquals(1, result.size());
    verify(repository).findByContextAndNames(ctx, List.of("ext_field_a"));
  }
}
