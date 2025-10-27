package com.bone.procurement.controller;

import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.dto.ErrorResponse;
import com.bone.procurement.service.PurchaseOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 采购订单控制器
 * 提供采购订单相关的REST API接口
 */
@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {
    private static final Logger log = LoggerFactory.getLogger(PurchaseOrderController.class);

    private final PurchaseOrderService purchaseOrderService;
    
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
        log.info("收到创建采购订单请求");
        return ResponseEntity.ok(purchaseOrderService.createOrder(order));
    }
    
    /**
     * 获取订单详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getOrder(@PathVariable Long id) {
        log.info("查询采购订单，ID: {}", id);
        PurchaseOrder order = purchaseOrderService.getOrder(id);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "NOT_FOUND", "订单不存在", "/api/purchase-orders/" + id));
        }
        return ResponseEntity.ok(order);
    }
    
    /**
     * 提交订单审批
     */
    @PostMapping("/{id}/submit")
    public ResponseEntity<?> submitForApproval(@PathVariable Long id) {
        log.info("提交订单审批，ID: {}", id);
        return ResponseEntity.ok(purchaseOrderService.submitForApproval(id));
    }
    
    /**
     * 审批订单
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<?> approveOrder(@PathVariable Long id, 
                                                    @RequestBody ApprovalRequest request) {
        log.info("审批订单，ID: {}, 审批人ID: {}", id, request.getApproverId());
        PurchaseOrder order = purchaseOrderService.approveOrder(
            id, 
            request.getApproverId().toString(), 
            request.getComments()
        );
        return ResponseEntity.ok(order);
    }
    
    /**
     * 执行订单（正式下单）
     * @param id 订单ID
     * @return 执行后的订单信息
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<?> executeOrder(@PathVariable Long id) {
        log.info("执行订单，ID: {}", id);
        return ResponseEntity.ok(purchaseOrderService.executeOrder(id));
    }
    
    /**
     * 取消订单
     * @param id 订单ID
     * @param request 包含取消原因的请求
     * @return 取消后的订单信息
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable Long id, 
                                                    @RequestBody CancellationRequest request) {
        log.info("取消订单，ID: {}, 原因: {}", id, request.getReason());
        
        return ResponseEntity.ok(purchaseOrderService.cancelOrder(id, request.getReason()));
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