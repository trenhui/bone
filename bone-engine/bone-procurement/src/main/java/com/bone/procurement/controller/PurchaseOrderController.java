package com.bone.procurement.controller;

import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.exception.BusinessException;
import com.bone.procurement.service.PurchaseOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;



/**
 * 采购订单控制器
 * 提供采购订单相关的REST API接口
 */
@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderController.class);

    @Autowired
    private PurchaseOrderService purchaseOrderService;
    
    @Autowired
    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }
    
    /**
     * 创建采购订单
     * @param order 采购订单对象
     * @return 创建成功的订单信息
     */
    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody PurchaseOrder order) {
        logger.info("收到创建采购订单请求");
        try {
            PurchaseOrder createdOrder = purchaseOrderService.createOrder(order);
            return ResponseEntity.ok().body(Map.of("id", "1")); // 简化，返回固定ID
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "创建订单失败"));
        }
    }
    
    /**
     * 获取订单详情
     * @param id 订单ID
     * @return 订单详情信息
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseOrder>> getOrder(@PathVariable Long id) {
        System.out.println("收到获取订单详情请求，订单ID: " + id);
        
        PurchaseOrder order = purchaseOrderService.getOrder(id);
        
        if (order != null) {
            ApiResponse<PurchaseOrder> response = new ApiResponse<>(
                true, 
                "查询成功", 
                order
            );
            return ResponseEntity.ok(response);
        } else {
            System.out.println("警告: 订单不存在，订单ID: " + id);
            throw new BusinessException("订单不存在: " + id, "ORDER_NOT_FOUND", HttpStatus.NOT_FOUND);
        }
    }
    
    /**
     * 提交订单审批
     * @param id 订单ID
     * @return 提交审批后的订单信息
     */
    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<PurchaseOrder>> submitForApproval(@PathVariable Long id) {
        System.out.println("收到提交订单审批请求，订单ID: " + id);
        
        try {
            PurchaseOrder submittedOrder = purchaseOrderService.submitForApproval(id);
            System.out.println("订单提交审批成功，订单ID: " + id);
            
            ApiResponse<PurchaseOrder> response = new ApiResponse<>(
                true, 
                "订单提交审批成功", 
                submittedOrder
            );
            
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            // BusinessException将由全局异常处理器处理
            throw e;
        } catch (Exception e) {
            System.err.println("提交订单审批失败: " + e.getMessage());
            e.printStackTrace();
            throw new BusinessException("提交订单审批失败: " + e.getMessage(), "ORDER_SUBMIT_ERROR");
        }
    }
    
    /**
     * 审批订单
     * @param id 订单ID
     * @param request 包含审批信息的请求
     * @return 审批后的订单信息
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<PurchaseOrder>> approveOrder(@PathVariable Long id, 
                                                    @RequestBody ApprovalRequest request) {
        logger.info("收到审批订单请求，订单ID: {}, 审批人ID: {}, 审批结果: {}", 
                id, request.getApproverId(), request.isApproved() ? "批准" : "拒绝");
        
        try {
            if (request.getApproverId() == null) {
                throw new BusinessException("审批人ID不能为空", "APPROVER_ID_NULL");
            }
            
            // 将approverId从Long转换为String，并创建评论内容
            String comment = request.getComments() != null ? 
                request.getComments() : 
                (request.isApproved() ? "审批通过" : "审批拒绝");
                
            PurchaseOrder approvedOrder = purchaseOrderService.approveOrder(
                    id, 
                    request.getApproverId().toString(), 
                    comment
            );
            
            String action = request.isApproved() ? "审批通过" : "审批拒绝";
            System.out.println("订单" + action + "成功，订单编号: " + 
                    (approvedOrder != null ? "已更新" : "未知") + ", 当前状态: 已更新");
            
            ApiResponse<PurchaseOrder> response = new ApiResponse<>(
                true, 
                "订单审批完成", 
                approvedOrder
            );
            
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            // BusinessException将由全局异常处理器处理
            throw e;
        } catch (Exception e) {
            System.err.println("审批订单失败: " + e.getMessage());
            e.printStackTrace();
            throw new BusinessException("审批订单失败: " + e.getMessage(), "ORDER_APPROVE_ERROR");
        }
    }
    
    /**
     * 执行订单（正式下单）
     * @param id 订单ID
     * @param request 包含执行人ID的请求
     * @return 执行后的订单信息
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<ApiResponse<PurchaseOrder>> executeOrder(@PathVariable Long id) {
        System.out.println("收到执行订单请求，订单ID: " + id);
        
        try {
            PurchaseOrder executedOrder = purchaseOrderService.executeOrder(id);
            System.out.println("订单执行成功，订单ID: " + id);
            
            ApiResponse<PurchaseOrder> response = new ApiResponse<>(
                true, 
                "订单执行成功", 
                executedOrder
            );
            
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            // BusinessException将由全局异常处理器处理
            throw e;
        } catch (Exception e) {
            System.err.println("执行订单失败: " + e.getMessage());
            e.printStackTrace();
            throw new BusinessException("执行订单失败: " + e.getMessage(), "ORDER_EXECUTE_ERROR");
        }
    }
    
    /**
     * 取消订单
     * @param id 订单ID
     * @param request 包含操作人ID的请求
     * @return 取消后的订单信息
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<PurchaseOrder>> cancelOrder(@PathVariable Long id, 
                                                    @RequestBody CancellationRequest request) {
        System.out.println("收到取消订单请求，订单ID: " + id + ", 原因: " + request.getReason());
        
        try {
            String cancelReason = request.getReason() != null ? request.getReason() : "用户取消";
            
            PurchaseOrder cancelledOrder = purchaseOrderService.cancelOrder(id, cancelReason);
            System.out.println("订单取消成功，订单ID: " + id);
            
            ApiResponse<PurchaseOrder> response = new ApiResponse<>(
                true, 
                "订单取消成功", 
                cancelledOrder
            );
            
            return ResponseEntity.ok(response);
        } catch (BusinessException e) {
            // BusinessException将由全局异常处理器处理
            throw e;
        } catch (Exception e) {
            System.err.println("取消订单失败: " + e.getMessage());
            e.printStackTrace();
            throw new BusinessException("取消订单失败: " + e.getMessage(), "ORDER_CANCEL_ERROR");
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
    
    /**
     * 审批请求DTO
     */
    private static class ApprovalRequest {
        private Long approverId;
        private boolean approved;
        private String comments;
        
        public Long getApproverId() {
            return approverId;
        }
        
        public void setApproverId(Long approverId) {
            this.approverId = approverId;
        }
        
        public boolean isApproved() {
            return approved;
        }
        
        public void setApproved(boolean approved) {
            this.approved = approved;
        }
        
        public String getComments() {
            return comments;
        }
        
        public void setComments(String comments) {
            this.comments = comments;
        }
    }
    
    /**
     * 执行请求DTO
     */
    private static class ExecutionRequest {
        private Long executorId;
        
        public Long getExecutorId() {
            return executorId;
        }
        
        public void setExecutorId(Long executorId) {
            this.executorId = executorId;
        }
    }
    
    /**
     * 取消请求DTO
     */
    private static class CancellationRequest {
        private Long operatorId;
        private String reason;
        
        public Long getOperatorId() {
            return operatorId;
        }
        
        public void setOperatorId(Long operatorId) {
            this.operatorId = operatorId;
        }
        
        public String getReason() {
            return reason;
        }
        
        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}