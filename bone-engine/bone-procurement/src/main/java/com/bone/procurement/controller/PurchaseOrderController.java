package com.bone.procurement.controller;

import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.service.PurchaseOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 采购订单Controller
 * 提供REST API接口来管理采购订单
 */
@RestController
@RequestMapping("/api/purchase-orders")
public class PurchaseOrderController {
    
    private final PurchaseOrderService purchaseOrderService;
    
    @Autowired
    public PurchaseOrderController(PurchaseOrderService purchaseOrderService) {
        this.purchaseOrderService = purchaseOrderService;
    }
    
    /**
     * 创建采购订单
     */
    @PostMapping
    public ResponseEntity<PurchaseOrder> createOrder(@RequestBody PurchaseOrder order) {
        PurchaseOrder createdOrder = purchaseOrderService.createOrder(order);
        return ResponseEntity.ok(createdOrder);
    }
    
    /**
     * 获取订单详情
     */
    @GetMapping("/{id}")
    public ResponseEntity<PurchaseOrder> getOrder(@PathVariable Long id) {
        PurchaseOrder order = purchaseOrderService.getOrder(id);
        if (order == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(order);
    }
    
    /**
     * 提交订单审批
     */
    @PostMapping("/{id}/submit")
    public ResponseEntity<PurchaseOrder> submitForApproval(@PathVariable Long id) {
        PurchaseOrder order = purchaseOrderService.submitForApproval(id);
        return ResponseEntity.ok(order);
    }
    
    /**
     * 审批订单
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<PurchaseOrder> approveOrder(@PathVariable Long id, 
                                                    @RequestBody Map<String, Long> request) {
        Long approverId = request.get("approverId");
        if (approverId == null) {
            return ResponseEntity.badRequest().build();
        }
        
        PurchaseOrder order = purchaseOrderService.approveOrder(id, approverId);
        return ResponseEntity.ok(order);
    }
    
    /**
     * 执行订单
     */
    @PostMapping("/{id}/execute")
    public ResponseEntity<PurchaseOrder> executeOrder(@PathVariable Long id) {
        PurchaseOrder order = purchaseOrderService.executeOrder(id);
        return ResponseEntity.ok(order);
    }
}