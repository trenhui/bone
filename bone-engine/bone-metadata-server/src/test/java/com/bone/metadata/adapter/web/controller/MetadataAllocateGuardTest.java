package com.bone.metadata.adapter.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.core.exception.BizException;
import com.bone.metadata.catalog.application.support.CatalogIdempotencySupport;
import com.bone.metadata.catalog.common.CatalogErrorCodes;
import com.bone.metadata.sdk.domain.model.FieldMetadata;
import com.bone.metadata.sdk.metadata.api.MetadataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code POST /api/v1/metadata/fields:allocate} 入参守卫测试。
 *
 * <p><b>缺陷背景（2026-10-26 全模块联调实测）</b>：{@link FieldMetadata} 是 SDK 的<b>零约束</b>领域模型， 且 {@code @Valid}
 * 标在 {@code List<FieldMetadata>} 上不会级联到元素 ⇒ 以下三类客户端错误<b>全部</b>漏进 SDK：
 *
 * <ul>
 *   <li>{@code AllocationContext} 缺项 ⇒ {@code Map.of(...)} 遇 null 抛 <b>NPE</b>；
 *   <li>{@code dataType} 非法 ⇒ {@code DataType.valueOf(...)} 抛 <b>IllegalArgumentException</b>；
 *   <li>预置 {@code columnName} ⇒ {@code validateField} 抛 <b>IllegalStateException</b>。
 * </ul>
 *
 * 三者均不携带业务语义 ⇒ 被 catch-all 兜成 <b>500 COMMON_INTERNAL_ERROR</b>——把「调用方传错」报成服务端故障， 污染 5xx 告警与
 * SLO。守卫须把它们归位 <b>400</b>。
 *
 * <p><b>判别力</b>：每个用例都断言「异常类型是 {@link BizException} 且 {@code code==400}」。 若守卫被摘掉，异常会变成 NPE /
 * IllegalArgumentException / IllegalStateException（非 BizException）⇒ 断言必然转红。
 */
class MetadataAllocateGuardTest {

  private MetadataController controller;
  private MetadataService metadataService;
  private CatalogIdempotencySupport idempotency;

  @BeforeEach
  void setUp() {
    metadataService = mock(MetadataService.class);
    idempotency = mock(CatalogIdempotencySupport.class);
    when(idempotency.replayRawBody(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
    controller = new MetadataController(metadataService, idempotency, new ObjectMapper());
  }

  /** 一条上下文完整的合法入参（appCode / bizIdentityCode / entityType / tenantId / name / dataType）。 */
  private static FieldMetadata valid() {
    FieldMetadata f = new FieldMetadata();
    f.setTenantId(0L);
    f.setAppCode("E2E");
    f.setBizIdentityCode("E2E_BIZ");
    f.setEntityType("ENTITY");
    f.setEntityCode("E2E_ALLOC");
    f.setName("e2e_field");
    f.setDataType("STRING");
    return f;
  }

  private BizException expectBadRequest(List<FieldMetadata> body) {
    return org.assertj.core.api.Assertions.catchThrowableOfType(
        () -> controller.allocateAndPersistFields(null, body), BizException.class);
  }

  @Test
  @DisplayName("★ 合法入参不被守卫误伤（守卫只挡非法，不放宽/收紧语义）")
  void validPayloadPasses() {
    when(metadataService.allocateAndPersistFields(any())).thenReturn(List.of(valid()));

    assertThatCode(() -> controller.allocateAndPersistFields(null, List.of(valid())))
        .as("合法入参必须放行；守卫写成恒真会把正常建模也挡在门外")
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("★ 缺 appCode/bizIdentityCode/entityType ⇒ 400（原为 NPE 兜成 500）")
  void missingAllocationContextIsBadRequest() {
    FieldMetadata noAppCode = valid();
    noAppCode.setAppCode(null);
    FieldMetadata noBiz = valid();
    noBiz.setBizIdentityCode("  ");
    FieldMetadata noEntityType = valid();
    noEntityType.setEntityType(null);

    for (FieldMetadata bad : List.of(noAppCode, noBiz, noEntityType)) {
      BizException ex = expectBadRequest(List.of(bad));
      assertThat(ex).as("缺失上下文必须抛 BizException（不是 NPE）").isNotNull();
      assertThat(ex.getCode()).as("入参非法是 400，不是 500").isEqualTo(400);
      assertThat(ex.getErrorCode()).isEqualTo(CatalogErrorCodes.FIELD_ALLOCATE_METADATA_INVALID);
    }
  }

  @Test
  @DisplayName("★ 非法 dataType 枚举 ⇒ 400（原为 IllegalArgumentException 兜成 500）")
  void unknownDataTypeIsBadRequest() {
    FieldMetadata f = valid();
    f.setDataType("VARCHAR");

    BizException ex = expectBadRequest(List.of(f));

    assertThat(ex).as("非法枚举必须抛 BizException（不是 IllegalArgumentException）").isNotNull();
    assertThat(ex.getCode()).isEqualTo(400);
    assertThat(ex.getErrorCode()).isEqualTo(CatalogErrorCodes.FIELD_ALLOCATE_METADATA_INVALID);
    assertThat(ex.getMessage()).contains("VARCHAR");
  }

  @Test
  @DisplayName("★ 预置 columnName ⇒ 400（原为 IllegalStateException 兜成 500）")
  void presetColumnNameIsBadRequest() {
    FieldMetadata f = valid();
    f.setColumnName("pre_set_col");

    BizException ex = expectBadRequest(List.of(f));

    assertThat(ex).as("预置物理列名必须抛 BizException（不是 IllegalStateException）").isNotNull();
    assertThat(ex.getCode()).isEqualTo(400);
    assertThat(ex.getErrorCode()).isEqualTo(CatalogErrorCodes.FIELD_ALLOCATE_METADATA_INVALID);
  }

  @Test
  @DisplayName("空列表 / null 元素 / 缺 tenantId·name ⇒ 400")
  void emptyAndNullishPayloadsAreBadRequest() {
    FieldMetadata noTenant = valid();
    noTenant.setTenantId(null);
    FieldMetadata noName = valid();
    noName.setName(null);

    List<FieldMetadata> withNullElement = new ArrayList<>();
    withNullElement.add(null);

    for (List<FieldMetadata> bad :
        List.<List<FieldMetadata>>of(
            List.of(), withNullElement, List.of(noTenant), List.of(noName))) {
      BizException ex = expectBadRequest(bad);
      assertThat(ex).as("非法载荷必须抛 BizException").isNotNull();
      assertThat(ex.getCode()).isEqualTo(400);
      assertThat(ex.getErrorCode()).isEqualTo(CatalogErrorCodes.FIELD_ALLOCATE_METADATA_INVALID);
    }
  }
}
