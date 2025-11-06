package com.bone.procurement.service;

import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 采购订单服务单元测试
 */
public class PurchaseOrderServiceTest {

    @Mock
    private PurchaseOrderRepository orderRepository;
    
    @Mock
    private PurchaseOrderStateMachine purchaseOrderStateMachine;
    
    @Mock
    private DistributedLockManager distributedLockManager;
    
    @Mock
    private BusinessRuleEngine businessRuleEngine;
    
    @InjectMocks
    private PurchaseOrderServiceImpl purchaseOrderService;
    
    private PurchaseOrder testOrder;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // 初始化测试订单
        testOrder = new PurchaseOrder();
        testOrder.setId(1L);
        testOrder.setOrderNumber("PO2024001");
        testOrder.setSupplierId("SUP001");
        testOrder.setDepartment("IT");
        testOrder.setCreator("user1");
        testOrder.setStatus("DRAFT");
        testOrder.setTotalAmount(new BigDecimal(1000));
    }
    
    @Test
    void testCreateOrderSuccess() throws BusinessException {
        // 准备数据
        when(orderRepository.save(any(PurchaseOrder.class))).thenReturn(testOrder);
        
        // 执行测试
        PurchaseOrder result = purchaseOrderService.createOrder(testOrder);
        
        // 验证结果
        assertNotNull(result);
        assertEquals("DRAFT", result.getStatus());
        verify(businessRuleEngine).validateOrder(any(PurchaseOrder.class));
        verify(orderRepository).save(any(PurchaseOrder.class));
    }
    
    @Test
    void testGetOrderSuccess() throws BusinessException {
        // 准备数据
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        
        // 执行测试
        PurchaseOrder result = purchaseOrderService.getOrder(1L);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }
    
    @Test
    void testGetOrderNotFound() {
        // 准备数据
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());
        
        // 执行测试并验证异常
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            purchaseOrderService.getOrder(1L);
        });
        
        assertEquals(HttpStatus.NOT_FOUND, exception.getHttpStatus());
    }
    
    @Test
    void testSubmitForApprovalSuccess() throws BusinessException {
        // 准备数据
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(PurchaseOrder.class))).thenReturn(testOrder);
        
        // 执行测试
        PurchaseOrder result = purchaseOrderService.submitForApproval(1L, "submitter1");
        
        // 验证结果
        assertNotNull(result);
        assertEquals("submitter1", result.getSubmitter());
        verify(purchaseOrderStateMachine).transition(any(PurchaseOrder.class), any());
    }
    
    @Test
    void testApproveOrderSuccess() throws BusinessException {
        // 准备数据
        testOrder.setStatus("SUBMITTED");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(PurchaseOrder.class))).thenReturn(testOrder);
        
        // 执行测试
        PurchaseOrder result = purchaseOrderService.approveOrder(1L, "approver1", "同意");
        
        // 验证结果
        assertNotNull(result);
        assertEquals("approver1", result.getApprover());
        verify(purchaseOrderStateMachine).transition(any(PurchaseOrder.class), any());
    }
    
    @Test
    void testCancelOrderSuccess() throws BusinessException {
        // 准备数据
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(PurchaseOrder.class))).thenReturn(testOrder);
        
        // 执行测试
        PurchaseOrder result = purchaseOrderService.cancelOrder(1L, "canceller1", "不再需要");
        
        // 验证结果
        assertNotNull(result);
        assertEquals("不再需要", result.getCancelReason());
        verify(purchaseOrderStateMachine).transition(any(PurchaseOrder.class), any());
    }
    
    @Test
    void testFindAllOrders() {
        // 准备数据
        List<PurchaseOrder> orderList = Arrays.asList(testOrder);
        Page<PurchaseOrder> page = new PageImpl<>(orderList);
        when(orderRepository.findAll(any(PageRequest.class))).thenReturn(page);
        
        // 执行测试
        List<PurchaseOrder> result = purchaseOrderService.findAllOrders(0, 10);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.size());
    }
}