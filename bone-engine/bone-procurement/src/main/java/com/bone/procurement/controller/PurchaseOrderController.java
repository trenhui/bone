package com.bone.procurement.controller;

import com.bone.procurement.dto.ApiResponse;
import com.bone.procurement.dto.ApprovalRequest;
import com.bone.procurement.dto.CancellationRequest;
import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.exception.BusinessException;
import com.bone.procurement.service.PurchaseOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 采购订单控制器
 * 提供采购订单相关的REST API接口
 */
@Api(tags = "采购订单管理", description = "提供采购订单的创建、查询、审批、执行等操作")
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
    @ApiOperation(value = "创建采购订单", notes = "创建新的采购订单，初始状态为草稿")
    @PostMapping
    public ResponseEntity<ApiResponse<PurchaseOrder>> createOrder(@RequestBody PurchaseOrder order) {
        log.info("收到创建采购订单请求");
        try {
            PurchaseOrder createdOrder = purchaseOrderService.createOrder(order);
            return ResponseEntity.ok(ApiResponse.success(createdOrder, "订单创建成功"));
        } catch (BusinessException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(ApiResponse.failure(e.getHttpStatus().value(), e.getMessage()));
        } catch (Exception e) {
            log.error("创建订单失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.failure(500, "创建订单失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取订单详情
     * @param id 订单ID
     * @return 订单详情
     */
    @ApiOperation(value = "获取订单详情", notes = "根据订单ID获取订单的详细信息")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PurchaseOrder>> getOrder(
            @ApiParam(value = "订单ID", required = true, example = "123") @PathVariable Long id) {
        log.info("查询采购订单，ID: {}", id);
        try {
            PurchaseOrder order = purchaseOrderService.getOrder(id);
            if (order == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.failure(HttpStatus.NOT_FOUND.value(), "订单不存在"));
            }
            return ResponseEntity.ok(ApiResponse.success(order));
        } catch (BusinessException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(ApiResponse.failure(e.getHttpStatus().value(), e.getMessage()));
        } catch (Exception e) {
            log.error("获取订单详情失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.failure(500, "获取订单详情失败: " + e.getMessage()));
        }
    }
    
    /**
     * 提交订单审批
     * @param id 订单ID
     * @param submitterId 提交人ID
     * @return 提交后的订单信息
     */
    @ApiOperation(value = "提交订单审批", notes = "将订单提交到审批流程")
    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<PurchaseOrder>> submitForApproval(
            @ApiParam(value = "订单ID", required = true, example = "123") @PathVariable Long id, 
            @ApiParam(value = "提交人ID", required = true) @RequestHeader(value = "User-Id", required = true) String submitterId) {
        log.info("提交订单审批，ID: {}, 提交人: {}", id, submitterId);
        try {
            PurchaseOrder order = purchaseOrderService.submitForApproval(id, submitterId);
            return ResponseEntity.ok(ApiResponse.success(order, "订单提交审批成功"));
        } catch (BusinessException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(ApiResponse.failure(e.getHttpStatus().value(), e.getMessage()));
        } catch (Exception e) {
            log.error("提交审批失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.failure(500, "提交审批失败: " + e.getMessage()));
        }
    }
    
    /**
     * 审批订单
     * @param id 订单ID
     * @param request 审批请求信息
     * @param approverId 审批人ID
     * @return 审批后的订单信息
     */
    @ApiOperation(value = "审批订单", notes = "审批通过采购订单")
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<PurchaseOrder>> approveOrder(
            @ApiParam(value = "订单ID", required = true, example = "123") @PathVariable Long id, 
            @ApiParam(value = "审批请求信息", required = true) @RequestBody ApprovalRequest request,
            @ApiParam(value = "审批人ID", required = true) @RequestHeader(value = "User-Id", required = true) String approverId) {
        log.info("审批订单，ID: {}, 审批人ID: {}", id, approverId);
        try {
            PurchaseOrder order = purchaseOrderService.approveOrder(
                id, 
                approverId, 
                request.getComments()
            );
            return ResponseEntity.ok(ApiResponse.success(order, "订单审批成功"));
        } catch (BusinessException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(ApiResponse.failure(e.getHttpStatus().value(), e.getMessage()));
        } catch (Exception e) {
            log.error("审批订单失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.failure(500, "审批订单失败: " + e.getMessage()));
        }
    }
    
    /**
     * 执行订单（正式下单）
     * @param id 订单ID
     * @return 执行后的订单信息
     */
    @ApiOperation(value = "执行订单", notes = "将审批通过的订单正式下单执行")
    @PostMapping("/{id}/execute")
    public ResponseEntity<ApiResponse<PurchaseOrder>> executeOrder(
            @ApiParam(value = "订单ID", required = true, example = "123") @PathVariable Long id) {
        log.info("执行订单，ID: {}", id);
        try {
            PurchaseOrder order = purchaseOrderService.executeOrder(id);
            return ResponseEntity.ok(ApiResponse.success(order, "订单执行成功"));
        } catch (BusinessException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(ApiResponse.failure(e.getHttpStatus().value(), e.getMessage()));
        } catch (Exception e) {
            log.error("执行订单失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.failure(500, "执行订单失败: " + e.getMessage()));
        }
    }
    
    /**
     * 取消订单
     * @param id 订单ID
     * @param request 包含取消原因的请求
     * @param cancellerId 取消人ID
     * @return 取消后的订单信息
     */
    @ApiOperation(value = "取消订单", notes = "取消指定的采购订单")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<PurchaseOrder>> cancelOrder(
            @ApiParam(value = "订单ID", required = true, example = "123") @PathVariable Long id, 
            @ApiParam(value = "取消请求信息", required = true) @RequestBody CancellationRequest request,
            @ApiParam(value = "取消人ID", required = true) @RequestHeader(value = "User-Id", required = true) String cancellerId) {
        log.info("取消订单，ID: {}, 取消人: {}, 原因: {}", id, cancellerId, request.getReason());
        try {
            PurchaseOrder order = purchaseOrderService.cancelOrder(id, cancellerId, request.getReason());
            return ResponseEntity.ok(ApiResponse.success(order, "订单已取消"));
        } catch (BusinessException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(ApiResponse.failure(e.getHttpStatus().value(), e.getMessage()));
        } catch (Exception e) {
            log.error("取消订单失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.failure(500, "取消订单失败: " + e.getMessage()));
        }
    }
    
    /**
     * 拒绝订单
     * @param id 订单ID
     * @param request 审批请求信息
     * @param approverId 审批人ID
     * @return 拒绝后的订单信息
     */
    @ApiOperation(value = "拒绝订单", notes = "拒绝当前审批的采购订单")
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<PurchaseOrder>> rejectOrder(
            @ApiParam(value = "订单ID", required = true, example = "123") @PathVariable Long id, 
            @ApiParam(value = "审批请求信息", required = true) @RequestBody ApprovalRequest request,
            @ApiParam(value = "审批人ID", required = true) @RequestHeader(value = "User-Id", required = true) String approverId) {
        log.info("拒绝订单，ID: {}, 审批人ID: {}", id, approverId);
        try {
            PurchaseOrder order = purchaseOrderService.rejectOrder(
                id, 
                approverId, 
                request.getComments()
            );
            return ResponseEntity.ok(ApiResponse.success(order, "订单已拒绝"));
        } catch (BusinessException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(ApiResponse.failure(e.getHttpStatus().value(), e.getMessage()));
        } catch (Exception e) {
            log.error("拒绝订单失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.failure(500, "拒绝订单失败: " + e.getMessage()));
        }
    }
    
// 已移除内部CancellationRequest类，使用外部导入的DTO类
}