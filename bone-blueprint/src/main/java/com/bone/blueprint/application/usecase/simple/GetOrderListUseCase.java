package com.bone.blueprint.application.usecase.simple;

import com.bone.blueprint.application.query.dto.OrderDto;
import com.bone.blueprint.application.query.qry.OrderPageQuery;
import com.bone.blueprint.application.query.handler.OrderPageQueryHandler;
import com.bone.core.usecase.UseCaseExecutor;
import com.bone.core.usecase.UseCase;
import com.bone.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 查询订单列表用例
 * <p>
 * 处理订单列表的查询请求
 * </p>
 */
@Slf4j
@UseCase(
    name = "GetOrderList",
    description = "查询订单列表",
    transactional = false
)
@Service
@RequiredArgsConstructor
public class GetOrderListUseCase implements UseCaseExecutor<OrderPageQuery, PageResult<OrderDto>> {

    private final OrderPageQueryHandler orderPageQueryHandler;

    /**
     * 执行订单列表查询
     * 
     * @param query 订单分页查询参数
     * @return 订单分页结果
     */
    @Override
    public PageResult<OrderDto> execute(OrderPageQuery query) {
        try {
            log.info("执行订单列表查询: pageNum={}, pageSize={}, customerId={}", 
                    query.getPageNum(), query.getPageSize(), query.getCustomerId());
            
            // 调用查询处理器执行查询
            PageResult<OrderDto> pageResult = orderPageQueryHandler.handle(query);
            
            log.info("订单列表查询成功: 共 {} 条记录, 总页数 {}", 
                    pageResult.getTotal(), pageResult.getPages());
            return pageResult;
        } catch (Exception e) {
            log.error("订单列表查询失败", e);
            throw new RuntimeException("查询订单列表失败", e);
        }
    }
}
