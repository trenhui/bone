package com.bone.procurement.service.controller;

import com.bone.procurement.engine.model.*;
import com.bone.procurement.service.PurchaseOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import javax.validation.Valid;
import javax.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 采购订单控制器
 * 提供采购订单的RESTful API接口，支持订单创建、更新、查询、审批等操作
 */
@Slf4j
@RestController
@RequestMapping("/api/purchase-orders")
@Api(tags = "采购订单管理")
@RequiredArgsConstructor
public class PurchaseOrderController {
    
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final String TENANT_ID_HEADER = "X-Tenant-ID";
    
    private final PurchaseOrderService purchaseOrderService;
    
    @ApiOperation("创建采购订单")
    @PostMapping
    public ResponseEntity<PurchaseOrder> createOrder(
            @ApiParam(value = "请求ID，用于跟踪", required = false) @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            @ApiParam(value = "采购订单对象", required = true) @Valid @RequestBody PurchaseOrder order) {
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        
        log.info("[{}] Received create order request", requestId);
        
        try {
            // 创建上下文
            RuleExecutionContext context = buildRuleContext(requestId);
            
            PurchaseOrder createdOrder = purchaseOrderService.createOrder(order, context);
            log.info("[{}] Order created successfully with ID: {}", requestId, createdOrder.getOrderId());
            
            return new ResponseEntity<>(createdOrder, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            log.error("[{}] Validation error when creating order: {}", requestId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("[{}] Error creating order", requestId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create order");
        }
    }
    
    @ApiOperation("更新采购订单")
    @PutMapping("/{orderId}")
    public ResponseEntity<PurchaseOrder> updateOrder(
            @ApiParam(value = "请求ID，用于跟踪", required = false) @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            @ApiParam(value = "订单ID", required = true) @PathVariable String orderId,
            @ApiParam(value = "采购订单对象", required = true) @Valid @RequestBody PurchaseOrder order) {
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        
        log.info("[{}] Received update order request for ID: {}", requestId, orderId);
        
        try {
            // 创建上下文
            RuleExecutionContext context = buildRuleContext(requestId);
            
            PurchaseOrder updatedOrder = purchaseOrderService.updateOrder(orderId, order, context);
            log.info("[{}] Order updated successfully: {}", requestId, orderId);
            
            return ResponseEntity.ok(updatedOrder);
        } catch (IllegalArgumentException e) {
            log.error("[{}] Validation error when updating order {}: {}", requestId, orderId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            log.error("[{}] Invalid state when updating order {}: {}", requestId, orderId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            log.error("[{}] Error updating order {}", requestId, orderId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to update order");
        }
    }
    
    @ApiOperation("根据ID获取采购订单")
    @GetMapping("/{orderId}")
    public ResponseEntity<PurchaseOrder> getOrderById(
            @ApiParam(value = "请求ID，用于跟踪", required = false) @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            @ApiParam(value = "订单ID", required = true) @PathVariable String orderId) {
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        
        log.info("[{}] Received get order request for ID: {}", requestId, orderId);
        
        try {
            PurchaseOrder order = purchaseOrderService.getOrderById(orderId);
            if (order == null) {
                log.warn("[{}] Order not found: {}", requestId, orderId);
                return ResponseEntity.notFound().build();
            }
            
            log.debug("[{}] Retrieved order: {}, status: {}", requestId, orderId, order.getStatus());
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            log.error("[{}] Error retrieving order {}", requestId, orderId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve order");
        }
    }
    
    @ApiOperation("根据编号获取采购订单")
    @GetMapping("/code/{orderCode}")
    public ResponseEntity<PurchaseOrder> getOrderByCode(
            @ApiParam(value = "请求ID，用于跟踪", required = false) @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            @ApiParam(value = "订单编号", required = true) @PathVariable String orderCode) {
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        
        log.info("[{}] Received get order request for code: {}", requestId, orderCode);
        
        try {
            PurchaseOrder order = purchaseOrderService.getOrderByCode(orderCode);
            if (order == null) {
                log.warn("[{}] Order not found with code: {}", requestId, orderCode);
                return ResponseEntity.notFound().build();
            }
            
            log.debug("[{}] Retrieved order by code: {}, ID: {}", requestId, orderCode, order.getOrderId());
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            log.error("[{}] Error retrieving order by code {}", requestId, orderCode, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve order");
        }
    }
    
    @ApiOperation("查询采购订单列表")
    @GetMapping
    public ResponseEntity<List<PurchaseOrder>> findOrders(
            @ApiParam(value = "请求ID，用于跟踪", required = false) @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            @ApiParam(value = "查询条件") @RequestParam Map<String, Object> conditions,
            @ApiParam(value = "页码，从1开始", defaultValue = "1") @RequestParam(defaultValue = "1") @Min(1) int page,
            @ApiParam(value = "每页数量", defaultValue = "10") @RequestParam(defaultValue = "10") @Min(1) int pageSize) {
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        
        log.info("[{}] Received find orders request with conditions: {}, page: {}, pageSize: {}", 
                 requestId, conditions, page, pageSize);
        
        try {
            List<PurchaseOrder> orders = purchaseOrderService.findOrdersByConditions(conditions, page, pageSize);
            log.info("[{}] Retrieved {} orders for page {}", requestId, orders.size(), page);
            
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            log.error("[{}] Error finding orders with conditions: {}", requestId, conditions, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to find orders");
        }
    }
    
    @ApiOperation("提交采购订单审批")
    @PostMapping("/{orderId}/submit")
    public ResponseEntity<ApprovalResult> submitForApproval(
            @ApiParam(value = "请求ID，用于跟踪", required = false) @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            @ApiParam(value = "订单ID", required = true) @PathVariable String orderId) {
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        
        log.info("[{}] Received submit for approval request for order ID: {}", requestId, orderId);
        
        try {
            // 创建上下文
            RuleExecutionContext context = buildRuleContext(requestId);
            
            ApprovalResult result = purchaseOrderService.submitForApproval(orderId, context);
            log.info("[{}] Order {} submitted for approval, next approver: {}", 
                     requestId, orderId, result.getNextApproverId());
            
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("[{}] Validation error when submitting order {} for approval: {}", requestId, orderId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            log.error("[{}] Invalid state when submitting order {} for approval: {}", requestId, orderId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            log.error("[{}] Error submitting order {} for approval", requestId, orderId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to submit order for approval");
        }
    }
    
    @ApiOperation("审批采购订单")
    @PostMapping("/{orderId}/approve")
    public ResponseEntity<ApprovalResult> approveOrder(
            @ApiParam(value = "请求ID，用于跟踪", required = false) @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            @ApiParam(value = "订单ID", required = true) @PathVariable String orderId,
            @ApiParam(value = "审批请求", required = true) @Valid @RequestBody ApprovalRequest request) {
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        
        log.info("[{}] Received approve order request for ID: {}, action: {}", 
                 requestId, orderId, request.getAction());
        
        try {
            // 创建上下文
            RuleExecutionContext context = buildRuleContext(requestId);
            
            ApprovalResult result = purchaseOrderService.approveOrder(
                    orderId, request.getAction(), request.getComment(), context);
            log.info("[{}] Order {} {} successfully by user: {}", 
                     requestId, orderId, request.getAction(), context.getUserId());
            
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.error("[{}] Validation error when approving order {}: {}", requestId, orderId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            log.error("[{}] Invalid state when approving order {}: {}", requestId, orderId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (SecurityException e) {
            log.error("[{}] Security error when approving order {}: {}", requestId, orderId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (Exception e) {
            log.error("[{}] Error approving order {}", requestId, orderId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to approve order");
        }
    }
    
    @ApiOperation("取消采购订单")
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<PurchaseOrder> cancelOrder(
            @ApiParam(value = "请求ID，用于跟踪", required = false) @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            @ApiParam(value = "订单ID", required = true) @PathVariable String orderId,
            @ApiParam(value = "取消请求", required = true) @Valid @RequestBody CancelRequest request) {
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        
        log.info("[{}] Received cancel order request for ID: {}, reason: {}", 
                 requestId, orderId, request.getReason());
        
        try {
            // 创建上下文
            RuleExecutionContext context = buildRuleContext(requestId);
            
            PurchaseOrder cancelledOrder = purchaseOrderService.cancelOrder(orderId, request.getReason(), context);
            log.info("[{}] Order {} cancelled successfully by user: {}", 
                     requestId, orderId, context.getUserId());
            
            return ResponseEntity.ok(cancelledOrder);
        } catch (IllegalArgumentException e) {
            log.error("[{}] Validation error when cancelling order {}: {}", requestId, orderId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            log.error("[{}] Invalid state when cancelling order {}: {}", requestId, orderId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            log.error("[{}] Error cancelling order {}", requestId, orderId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to cancel order");
        }
    }
    
    @ApiOperation("关闭采购订单")
    @PostMapping("/{orderId}/close")
    public ResponseEntity<PurchaseOrder> closeOrder(
            @ApiParam(value = "请求ID，用于跟踪", required = false) @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            @ApiParam(value = "订单ID", required = true) @PathVariable String orderId) {
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        
        log.info("[{}] Received close order request for ID: {}", requestId, orderId);
        
        try {
            // 创建上下文
            RuleExecutionContext context = buildRuleContext(requestId);
            
            PurchaseOrder closedOrder = purchaseOrderService.closeOrder(orderId, context);
            log.info("[{}] Order {} closed successfully by user: {}", 
                     requestId, orderId, context.getUserId());
            
            return ResponseEntity.ok(closedOrder);
        } catch (IllegalArgumentException e) {
            log.error("[{}] Validation error when closing order {}: {}", requestId, orderId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            log.error("[{}] Invalid state when closing order {}: {}", requestId, orderId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (Exception e) {
            log.error("[{}] Error closing order {}", requestId, orderId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to close order");
        }
    }
    
    @ApiOperation("验证采购订单")
    @PostMapping("/validate")
    public ResponseEntity<List<RuleValidationResult>> validateOrder(
            @ApiParam(value = "请求ID，用于跟踪", required = false) @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            @ApiParam(value = "采购订单对象", required = true) @Valid @RequestBody PurchaseOrder order) {
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        
        log.info("[{}] Received validate order request", requestId);
        
        try {
            // 创建上下文
            RuleExecutionContext context = buildRuleContext(requestId);
            
            List<RuleValidationResult> results = purchaseOrderService.validateOrder(order, context);
            
            // 统计验证结果
            long errorCount = results.stream().filter(r -> !r.isPassed()).count();
            long warningCount = results.stream().filter(r -> "WARNING".equals(r.getSeverity())).count();
            
            log.info("[{}] Order validation completed: {} errors, {} warnings", 
                     requestId, errorCount, warningCount);
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("[{}] Error validating order", requestId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to validate order");
        }
    }
    
    @ApiOperation("获取订单统计信息")
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getOrderStatistics(
            @ApiParam(value = "请求ID，用于跟踪", required = false) @RequestHeader(value = REQUEST_ID_HEADER, required = false) String requestId,
            @ApiParam(value = "统计条件") @RequestParam Map<String, Object> conditions) {
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        
        log.info("[{}] Received get order statistics request with conditions: {}", requestId, conditions);
        
        try {
            Map<String, Object> statistics = purchaseOrderService.getOrderStatistics(conditions);
            log.info("[{}] Order statistics generated successfully", requestId);
            
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("[{}] Error generating order statistics", requestId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate order statistics");
        }
    }
    
    // 构建规则执行上下文
    private RuleExecutionContext buildRuleContext(String requestId) {
        // 从认证上下文获取当前用户信息
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = "current_user"; // 默认用户
        String userName = "当前用户"; // 默认用户名
        
        if (auth != null && auth.isAuthenticated() && !(auth.getPrincipal() instanceof String)) {
            // 在实际应用中，这里应该从Principal对象中获取用户信息
            userId = auth.getName();
            userName = auth.getName(); // 实际应用中可能需要从UserDetails获取
        }
        
        // 从请求头或上下文中获取租户ID
        // 这里简化实现，实际应该从请求头或线程本地变量中获取
        String tenantId = "default"; // 默认租户
        
        return RuleExecutionContext.builder()
                .tenantId(tenantId)
                .userId(userId)
                .userName(userName)
                .requestId(requestId)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    // 请求参数模型类
    static class ApprovalRequest {
        @javax.validation.constraints.NotBlank(message = "Action is required")
        private String action; // approve 或 reject
        private String comment;
        
        // Getters and Setters
        public String getAction() {
            return action;
        }
        
        public void setAction(String action) {
            this.action = action;
        }
        
        public String getComment() {
            return comment;
        }
        
        public void setComment(String comment) {
            this.comment = comment;
        }
    }
    
    static class CancelRequest {
        @javax.validation.constraints.NotBlank(message = "Cancellation reason is required")
        private String reason;
        
        // Getters and Setters
        public String getReason() {
            return reason;
        }
        
        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}