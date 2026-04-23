package com.bone.blueprint.application.usecase.simple;

import com.bone.blueprint.application.command.cmd.CancelOrderCommand;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import com.bone.blueprint.domain.gateway.InventoryGateway;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 取消订单用例
 * <p>
 * 简单取消订单，适用于内部管理后台
 * </p>
 */
@Slf4j
@UseCase(
    name = "CancelOrder",
    description = "简单取消订单，适用于内部管理后台",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class CancelOrderUseCase implements UseCaseExecutor<CancelOrderCommand, Void> {

    private final OrderRepository orderRepository;
    private final InventoryGateway inventoryGateway;

    /**
     * 执行取消订单操作
     * 
     * @param cmd 取消订单命令
     * @return 无返回值
     */
    @Override
    public Void execute(CancelOrderCommand cmd) {
        try {
            log.info("执行取消订单操作: orderId={}", cmd.getOrderId());
            
            Order order = orderRepository.findById(cmd.getOrderId());
            if (order == null) {
                log.warn("订单不存在: orderId={}", cmd.getOrderId());
                throw new NotFoundException("订单不存在");
            }
            
            order.cancel();
            orderRepository.save(order);
            
            log.info("订单状态已更新为取消: orderId={}", cmd.getOrderId());
            
            inventoryGateway.releaseStock(order.getId());
            log.info("库存已释放: orderId={}", cmd.getOrderId());
            
            return null;
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("取消订单失败: orderId={}", cmd.getOrderId(), e);
            throw new RuntimeException("取消订单失败", e);
        }
    }
}
