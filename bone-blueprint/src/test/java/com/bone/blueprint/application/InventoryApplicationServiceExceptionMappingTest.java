package com.bone.blueprint.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.bone.blueprint.application.port.out.TenantPort;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.domain.model.inventory.Inventory;
import com.bone.blueprint.domain.repository.InventoryRepository;
import com.bone.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * {@link InventoryApplicationService} 的异常落点契约。
 *
 * <p><b>为何必须有此测试</b>（2026-10-07 实测修复的真实缺陷）：应用层的 {@code catch (DomainException)} <b>catch 不到</b>
 * 域聚合抛出的 {@link IllegalArgumentException}（数量非正，{@code delta <= 0}） ⇒ 异常穿透到 {@code
 * GlobalExceptionHandler} 的 {@code @ExceptionHandler(Exception.class)} 兜底 ⇒ 被报成 <b>HTTP
 * 500</b>，把「数量参数非法」这类**用户可纠正**的业务错误混进 5xx 错误预算、 污染告警与 SLO，并误导客户端重试（重试无用，数量依然非法）。
 *
 * <p><b>修法与取舍</b>：域层继续抛 {@code IllegalArgumentException}（<b>不改</b>）—— 它是业界惯例（Spring/SQL/JDK
 * 内部都用它表达入参非法），改成 {@code DomainException} 会让框架对该异常的契约识别失效。在**应用层补 catch 翻译**才是职责正确的位置。
 *
 * <p><b>状态码语义区分</b>：数量非法（{@code <= 0}）是 <b>400</b>（入参非法）， 与「可用量不足」的 <b>409</b>（冲突，调用方需减少数量或换仓）不可混用。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryApplicationServiceExceptionMappingTest {

  private static final Long TENANT = 1L;
  private static final Long PRODUCT = 100L;
  private static final String WAREHOUSE = "WH1";

  @Mock private TenantPort tenantProvider;
  @Mock private InventoryRepository inventoryRepository;
  @InjectMocks private InventoryApplicationService service;

  private Inventory existing(int available) {
    // 真实签名（实测 Inventory#create，勿凭直觉改）：
    // create(long id, Long tenantId, Long productId, String productName,
    //        String warehouseCode, int initialQty, int safetyStock)
    return Inventory.create(9001L, TENANT, PRODUCT, "商品", WAREHOUSE, available, 0);
  }

  private void givenInventory(int available) {
    when(tenantProvider.currentTenantId()).thenReturn(TENANT);
    // ⚠️⚠️ 两个坑（实测踩过）：
    // 1) 仓储接口**没有** insert/update 自有方法（继承自 Repository 父接口）⇒ 桩打在这些方法上编译不过。
    // 2) findByProduct 是 **default 方法**，Mockito 默认**拦截**它并返回 null
    //    ⇒ 须 thenCallRealMethod，否则服务拿到 null 抛 404 INVENTORY_NOT_FOUND，
    //    测试根本没走到被测分支，却「看起来像断言失败」。
    when(inventoryRepository.findByProduct(anyLong(), any(), any()))
        .thenAnswer(inv -> existing(available));
    // 写路径来自父接口 Repository：**insert 返回 ID、update 返回 boolean**（实测签名），
    // 返回类型写错会让 Mockito 桩在运行期抛 Unresolved compilation problems。
    when(inventoryRepository.insert(any())).thenReturn(9001L);
    when(inventoryRepository.update(any())).thenReturn(true);
  }

  @Test
  @DisplayName("★ 数量非法（delta<=0）⇒ 400 + BP_INVENTORY_QUANTITY_INVALID，绝不是 500")
  void nonPositiveDeltaMapsTo400() {
    givenInventory(50);

    assertThatThrownBy(() -> service.deduct(PRODUCT, WAREHOUSE, 0))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 400)
        .hasFieldOrPropertyWithValue("errorCode", BlueprintErrorCodes.INVENTORY_QUANTITY_INVALID);
  }

  @Test
  @DisplayName("数量非法在五个写入口全部生效（receive/deduct/reserve/confirm/release）")
  void allFiveWritePathsMapQuantityError() {
    givenInventory(50);

    // 逐一验证 5 个入口都做了翻译——缺一处就漏一处，逐个断言才能定位
    assertThatThrownBy(() -> service.receive(PRODUCT, "商品", WAREHOUSE, -1))
        .hasFieldOrPropertyWithValue("errorCode", BlueprintErrorCodes.INVENTORY_QUANTITY_INVALID);
    assertThatThrownBy(() -> service.deduct(PRODUCT, WAREHOUSE, -1))
        .hasFieldOrPropertyWithValue("errorCode", BlueprintErrorCodes.INVENTORY_QUANTITY_INVALID);
    assertThatThrownBy(() -> service.reserve(PRODUCT, WAREHOUSE, 0))
        .hasFieldOrPropertyWithValue("errorCode", BlueprintErrorCodes.INVENTORY_QUANTITY_INVALID);
    assertThatThrownBy(() -> service.confirm(PRODUCT, WAREHOUSE, 0))
        .hasFieldOrPropertyWithValue("errorCode", BlueprintErrorCodes.INVENTORY_QUANTITY_INVALID);
    assertThatThrownBy(() -> service.release(PRODUCT, WAREHOUSE, 0))
        .hasFieldOrPropertyWithValue("errorCode", BlueprintErrorCodes.INVENTORY_QUANTITY_INVALID);
  }

  @Test
  @DisplayName("语义区分：可用量不足仍是 409 + INVENTORY_INSUFFICIENT（未被新 catch 抢走）")
  void insufficientStillMapsTo409() {
    givenInventory(1); // 可用 1，请求扣 5 ⇒ 不足

    assertThatThrownBy(() -> service.deduct(PRODUCT, WAREHOUSE, 5))
        .isInstanceOf(BizException.class)
        .hasFieldOrPropertyWithValue("code", 409)
        .hasFieldOrPropertyWithValue("errorCode", BlueprintErrorCodes.INVENTORY_INSUFFICIENT);
  }

  @Test
  @DisplayName("errorCode 非空：前端 i18n 键存在（ProblemDetail.errorCode 不为 null）")
  void errorCodeIsNeverNull() {
    givenInventory(50);

    assertThatThrownBy(() -> service.deduct(PRODUCT, WAREHOUSE, 0))
        .satisfies(
            ex -> {
              BizException biz = (BizException) ex;
              assertThat(biz.getErrorCode())
                  .as("无 errorCode ⇒ 前端拿不到 i18n.t('errors.'+code) 的键，只能展示中文兜底")
                  .isNotNull();
              assertThat(biz.getErrorCode()).startsWith("BP_");
            });
  }
}
