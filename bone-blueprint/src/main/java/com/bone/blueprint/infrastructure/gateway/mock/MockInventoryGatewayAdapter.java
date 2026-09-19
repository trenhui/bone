package com.bone.blueprint.infrastructure.gateway.mock;

import com.bone.blueprint.domain.gateway.InventoryGateway;
import org.springframework.stereotype.Component;

/**
 * 库存 ACL 出站适配器（Mock 实现，供样板工程本地演示）。
 *
 * <p><b>样板占位警告（生产前必须替换）</b>：当前为 Mock 实现，仅用于本地跑通下单→支付→库存流程演示， 并非真实库存服务对接——{@link #checkStock}
 * 恒放行（不校验真实库存），{@code reserve/confirm/release} 为无操作。 接入真实库存服务前必须：① 实现远程客户端（Feign / HTTP）并做协议转换；②
 * 外部错误语义/空值在此边界转为领域异常（E-10），禁止把外部 {@code null}/异常穿透到 domain；③ 补充<b>契约测试</b>（打桩外部响应，验证翻译与错误语义隔离，E-10
 * 强制项）。
 */
@Component
public class MockInventoryGatewayAdapter implements InventoryGateway {

  @Override
  public boolean checkStock(Long productId, Integer quantity) {
    // 占位：真实实现对库存服务发起校验，此处仅做参数非空放行，便于本地演示。
    return productId != null && quantity != null && quantity > 0;
  }

  @Override
  public void reserveStock(Long orderId, Long productId, Integer quantity) {
    // 真实实现：远程调用库存服务预留接口（待接入）
  }

  @Override
  public void confirmStock(Long orderId, Long productId, Integer quantity) {
    // 真实实现：远程调用库存服务确认扣减接口（待接入）
  }

  @Override
  public void releaseStock(Long orderId) {
    // 真实实现：远程调用库存服务释放预留接口（待接入）
  }
}
