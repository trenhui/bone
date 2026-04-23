package com.bone.blueprint.application.usecase.standard;

import com.bone.blueprint.application.command.cmd.CreateOrderCommand;
import com.bone.blueprint.application.command.handler.CheckStockHandler;
import com.bone.blueprint.application.command.handler.CreateOrderHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import com.bone.blueprint.domain.extension.order.OrderPriceCalculator;
import com.bone.blueprint.domain.order.Order;
import com.bone.blueprint.domain.order.OrderItem;
import com.bone.blueprint.domain.repository.OrderRepository;
import com.bone.core.util.DistributedIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 创建订单用例
 * <p>
 * 标准订单创建，支持 AI 编排与多租户扩展
 * </p>
 */
@Slf4j
@UseCase(
    name = "CreateOrder",
    description = "标准订单创建，支持 AI 编排与多租户扩展",
    transactional = true
)
@Service
@RequiredArgsConstructor
public class CreateOrderUseCase implements UseCaseExecutor<CreateOrderCommand, Long> {

    private final CheckStockHandler checkStockHandler;
    private final CreateOrderHandler createOrderHandler;
    private final OrderRepository orderRepository;
    private final OrderPriceCalculator priceCalculator;

    /**
     * 执行创建订单操作
     * 
     * @param cmd 创建订单命令
     * @return 订单ID
     */
    @Override
    public Long execute(CreateOrderCommand cmd) {
        try {
            log.info("执行创建订单操作: customerId={}, itemsCount={}", 
                    cmd.getCustomerId(), cmd.getItems().size());
            
            // 1. 生成订单ID
            long orderId = DistributedIdGenerator.generateLongId();
            log.info("生成订单ID: {}", orderId);
            
            // 2. 构建订单明细
            List<OrderItem> items = cmd.getItems().stream()
                .map(dto -> OrderItem.create(
                    DistributedIdGenerator.generateLongId(),
                    orderId,
                    dto.getProductId(),
                    dto.getProductName(),
                    dto.getQuantity(),
                    dto.getUnitPrice()))
                .collect(Collectors.toList());
            log.info("构建订单明细完成: {} 个商品", items.size());
            
            // 3. 校验库存
            checkStockHandler.handle(items);
            log.info("库存校验通过");
            
            // 4. 创建订单
            Order order = createOrderHandler.handle(orderId, cmd.getCustomerId(), items);
            log.info("创建订单对象完成");
            
            // 5. 计算最终价格（扩展点）
            BigDecimal finalPrice = priceCalculator.calculate(
                OrderPriceCalculator.OrderPriceRequest.builder()
                    .baseAmount(order.getTotalAmount())
                    .shippingFee(BigDecimal.ZERO)
                    .build()
            );
            order.updateTotalAmount(finalPrice);
            log.info("计算最终价格完成: {}", finalPrice);
            
            orderRepository.save(order);
            log.info("订单保存成功: orderId={}", orderId);
            
            return orderId;
        } catch (Exception e) {
            log.error("创建订单失败", e);
            throw new RuntimeException("创建订单失败", e);
        }
    }
}
