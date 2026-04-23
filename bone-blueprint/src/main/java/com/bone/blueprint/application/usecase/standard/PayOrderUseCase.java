package com.bone.blueprint.application.usecase.standard;

import com.bone.blueprint.application.command.cmd.PayOrderCommand;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 支付订单用例
 * <p>
 * 标准订单支付，支持多租户扩展
 * </p>
 */
@Slf4j
@UseCase(
    name = "PayOrder",
    description = "标准订单支付，支持多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class PayOrderUseCase implements UseCaseExecutor<PayOrderCommand, Void> {

    private final OrderRepository orderRepository;

    /**
     * 执行订单支付操作
     * 
     * @param cmd 支付订单命令
     * @return 无返回值
     */
    @Override
    public Void execute(PayOrderCommand cmd) {
        try {
            log.info("执行订单支付操作: orderId={}", cmd.getOrderId());
            
            Order order = orderRepository.findById(cmd.getOrderId());
            if (order == null) {
                log.warn("订单不存在: orderId={}", cmd.getOrderId());
                throw new NotFoundException("订单不存在");
            }
            
            order.pay();
            orderRepository.save(order);
            
            log.info("订单支付成功: orderId={}", cmd.getOrderId());
            
            return null;
        } catch (NotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("订单支付失败: orderId={}", cmd.getOrderId(), e);
            throw new RuntimeException("订单支付失败", e);
        }
    }
}
