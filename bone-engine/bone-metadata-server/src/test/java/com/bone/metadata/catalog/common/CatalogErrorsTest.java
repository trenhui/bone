package com.bone.metadata.catalog.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link CatalogErrors} 不变量测试。
 *
 * <p><b>为何必须有</b>：本类此前<b>不存在</b> —— 模块只有 {@link CatalogErrorCodes} 而无状态表， 于是全部抛出点退化为 {@code
 * BizException.of(...)}，而那些重载<b>无一携带 errorCode</b> ⇒ {@code ProblemDetail.errorCode} 恒空、状态被兜底成 500。
 * 新建工厂后必须锁住两个不变量，否则会退化成「文档说有 fail-fast，实际没有」：
 *
 * <ol>
 *   <li><b>码 ⇒ 状态派生</b>：{@code httpStatusOf} 对未登记码<b>抛异常</b>而非兜底 （兜底 = 把「查不到」静默降级成 400 / 「冲突」降级成
 *       500，污染 5xx 告警与 SLO）；
 *   <li><b>码随异常走</b>：{@code of(...)} 把码写进 {@code getErrorCode()}， <b>不是</b>拼进 message ——
 *       否则前端只能靠字符串切割拿码，文案一改即断。
 * </ol>
 */
class CatalogErrorsTest {

  @Test
  @DisplayName("每个码都能派生出 HTTP 状态，且未登记码 fail fast（不兜底）")
  void httpStatusDerivation() {
    assertThat(CatalogErrors.httpStatusOf(CatalogErrorCodes.ENTITY_NOT_FOUND)).isEqualTo(404);
    assertThat(CatalogErrors.httpStatusOf(CatalogErrorCodes.ENTITY_CODE_CONFLICT)).isEqualTo(409);
    assertThat(CatalogErrors.httpStatusOf(CatalogErrorCodes.ENTITY_COPY_CODE_REQUIRED))
        .isEqualTo(400);

    // 未登记 ⇒ 必须抛，而不是默默返回 400
    assertThatThrownBy(() -> CatalogErrors.httpStatusOf("META_NOT_REGISTERED_ANYWHERE"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("未登记默认 HTTP 状态");
  }

  @Test
  @DisplayName("码写进 errorCode 字段（前端 i18n 的键），而非拼进 message")
  void codeTravelsInFieldNotMessage() {
    BizException ex = CatalogErrors.of(CatalogErrorCodes.ENTITY_NOT_FOUND, 42L);

    assertThat(ex.getErrorCode())
        .as("ProblemDetail.errorCode 的来源；为 null 则前端拿不到 i18n 键、监控只能按中文聚合")
        .isEqualTo(CatalogErrorCodes.ENTITY_NOT_FOUND);
    assertThat(ex.getCode()).as("HTTP 状态").isEqualTo(404);
    assertThat(ex.getMessage())
        .as("message 仍是「码: 上下文」形态，供人读")
        .isEqualTo(CatalogErrorCodes.ENTITY_NOT_FOUND + ": 42");
  }

  @Test
  @DisplayName("★ 回归锁定：BizException.of(...) 全部不携带 errorCode（这正是本工厂存在的原因）")
  void plainOfOverloadsCarryNoErrorCode() {
    // 这些重载已在 BizException 上标 @Deprecated；本断言的作用是让「它们不携带码」成为可回归事实，
    // 一旦哪天有人给 of() 加了码，本测试红⇒ 可评估是否改用 of() 路径。
    assertThat(new BizException("x").getErrorCode()).isNull();
    assertThat(new BizException(404, "x").getErrorCode()).isNull();
    assertThat(new BizException(404, "x", (Throwable) null).getErrorCode()).isNull();
    assertThat(BizException.of("x").getErrorCode()).isNull();
    assertThat(BizException.of(404, "x").getErrorCode()).isNull();
    assertThat(BizException.of(404, "x", new RuntimeException()).getErrorCode()).isNull();
  }

  @Test
  @DisplayName("状态纠偏：原先被 of(String) 吞成 500 的「编码/表名已存在」现为 409")
  void occupiedConflictNoLongerReports500() {
    // 迁移前：BizException.of(describeOccupied(...)) ⇒ 默认 500 ⇒ 污染服务端故障告警
    assertThat(CatalogErrors.of(CatalogErrorCodes.ENTITY_CODE_CONFLICT, "实体编码已存在").getCode())
        .isEqualTo(409);
    assertThat(CatalogErrors.of(CatalogErrorCodes.ENTITY_TABLE_NAME_CONFLICT, "实体表名已存在").getCode())
        .isEqualTo(409);
  }

  @Test
  @DisplayName("supplier 延迟构造器产出的异常与直接构造等价（供 Optional.orElseThrow 用）")
  void supplierMatchesDirectConstruction() {
    BizException direct = CatalogErrors.of(CatalogErrorCodes.FIELD_NOT_FOUND, 7L);
    BizException viaSupplier = CatalogErrors.supplier(CatalogErrorCodes.FIELD_NOT_FOUND, 7L).get();

    assertThat(viaSupplier.getErrorCode()).isEqualTo(direct.getErrorCode());
    assertThat(viaSupplier.getCode()).isEqualTo(direct.getCode());
    assertThat(viaSupplier.getMessage()).isEqualTo(direct.getMessage());
  }
}
