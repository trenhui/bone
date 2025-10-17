package com.bone.procurement.controller;

import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.service.PurchaseOrderService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;



/**
 * 采购订单控制器
 * 提供采购订单相关的REST API接口
 */
@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderController.class);
    
    private final PurchaseOrderService purchaseOrderService;
    
    @Autowired
    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }
    
    /**
     * 创建采购订单
     * @param order 采购订单对象
     * @return 创建成功的订单信息
     * @throws ResponseStatusException 参数验证失败或业务逻辑错误时抛出
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseOrder>> createOrder(@RequestBody PurchaseOrder order) {
        logger.info("收到创建采购订单请求: {}", order.getOrderCode());
        
        try {
            PurchaseOrder createdOrder = purchaseOrderService.createOrder(order);
            logger.info("采购订单创建成功，订单ID: {}", createdOrder.getId());
            
            ApiResponse<PurchaseOrder> response = new ApiResponse<>(
                true, 
                "采购订单创建成功", 
                createdOrder
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            logger.warn("参数验证失败: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("创建采购订单失败: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "创建采购订单失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取订单详情
     * @param id 订单ID
     * @return 订单详情信息
     * @throws ResponseStatusException 订单不存在时抛出404
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseOrder>> getOrder(@PathVariable Long id) {
        logger.info("收到获取订单详情请求，订单ID: {}", id);
        
        PurchaseOrder order = purchaseOrderService.getOrder(id);
        
        if (order != null) {
            ApiResponse<PurchaseOrder> response = new ApiResponse<>(
                true, 
                "查询成功", 
                order
            );
            return ResponseEntity.ok(response);
        } else {
            logger.warn("订单不存在，订单ID: {}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "订单不存在: " + id);
        }
    }
    
    /**
     * 提交订单审批
     * @param id 订单ID
     * @return 提交审批后的订单信息
     * @throws ResponseStatusException 参数错误或业务逻辑错误时抛出
     */
    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<PurchaseOrder>> submitForApproval(@PathVariable Long id) {
        logger.info("收到提交订单审批请求，订单ID: {}", id);
        
        try {
            PurchaseOrder submittedOrder = purchaseOrderService.submitForApproval(id);
            logger.info("订单提交审批成功，当前审批节点: {}", submittedOrder.getCurrentApprovalNode());
            
            ApiResponse<PurchaseOrder> response = new ApiResponse<>(
                true, 
                "订单已提交审批", 
                submittedOrder
            );
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("参数验证失败: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.error("提交订单审批失败: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("提交订单审批失败: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "提交订单审批失败", e);
        }
    }
    
    /**
     * 审批订单
     * @param id 订单ID
     * @param request 包含审批人ID的请求
     * @return 审批后的订单信息
     * @throws ResponseStatusException 参数错误或业务逻辑错误时抛出
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<PurchaseOrder>> approveOrder(@PathVariable Long id, 
                                                    @RequestBody Map<String, Long> request) {
        Long approverId = request.get("approverId");
        if (approverId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "审批人ID不能为空");
        }
        
        logger.info("收到审批订单请求，订单ID: {}, 审批人ID: {}", id, approverId);
        
        try {
            PurchaseOrder approvedOrder = purchaseOrderService.approveOrder(id, approverId);
            logger.info("订单审批成功，当前状态: {}", approvedOrder.getOrderStatus());
            
            ApiResponse<PurchaseOrder> response = new ApiResponse<>(
                true, 
                "订单审批成功", 
                approvedOrder
            );
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("参数验证失败: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.error("审批订单失败: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("审批订单失败: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "审批订单失败", e);
        }
    }
    
    /**
     * 执行订单（正式下单）
     * @param id 订单ID
     * @return 执行后的订单信息
     * @throws ResponseStatusException 参数错误或业务逻辑错误时抛出
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<ApiResponse<PurchaseOrder>> executeOrder(@PathVariable Long id) {
        logger.info("收到执行订单请求，订单ID: {}", id);
        
        try {
            PurchaseOrder executedOrder = purchaseOrderService.executeOrder(id);
            logger.info("订单执行成功，订单状态: {}", executedOrder.getOrderStatus());
            
            ApiResponse<PurchaseOrder> response = new ApiResponse<>(
                true, 
                "订单执行成功", 
                executedOrder
            );
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            logger.warn("参数验证失败: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (RuntimeException e) {
            logger.error("执行订单失败: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            logger.error("执行订单失败: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "执行订单失败", e);
        }
    }
    
    /**
     * API响应统一封装类
     */
    private static class ApiResponse<T> {
        private final boolean success;
        private final String message;
        private final T data;
        private final long timestamp;
        
        public ApiResponse(boolean success, String message, T data) {
            this.success = success;
            this.message = message;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public String getMessage() {
            return message;
        }
        
        public T getData() {
            return data;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
    }
}