package com.bone.procurement.repository;

import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.entity.PurchaseOrderItem;
import com.bone.procurement.model.OrderQueryCriteria;
import com.bone.procurement.model.OrderStatus;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 采购订单仓储接口
 * 提供订单数据的CRUD操作和高级查询功能
 */
public interface PurchaseOrderRepository {
    // 基础CRUD方法（保留原有方法的兼容）
    PurchaseOrder save(PurchaseOrder order);
    PurchaseOrder findById(Long id);
    void deleteById(Long id);
    
    /**
     * 创建采购订单
     * @param order 采购订单对象
     * @return 创建的订单
     */
    PurchaseOrder create(PurchaseOrder order);
    
    /**
     * 根据ID查询订单（返回Optional）
     * @param id 订单ID
     * @return 订单对象（如果存在）
     */
    Optional<PurchaseOrder> findByIdOptional(Long id);
    
    /**
     * 根据条件查询订单列表
     * @param criteria 查询条件
     * @return 符合条件的订单列表
     */
    List<PurchaseOrder> findByCriteria(OrderQueryCriteria criteria);
    
    /**
     * 分页查询订单
     * @param criteria 查询条件
     * @param page 页码（从1开始）
     * @param size 每页大小
     * @return 分页结果
     */
    PageResult<PurchaseOrder> findByCriteriaWithPagination(OrderQueryCriteria criteria, int page, int size);
    
    /**
     * 更新订单信息
     * @param order 订单对象
     * @return 更新后的订单
     */
    PurchaseOrder update(PurchaseOrder order);
    
    /**
     * 删除订单
     * @param id 订单ID
     */
    void delete(Long id);
    
    /**
     * 批量删除订单
     * @param ids 订单ID列表
     */
    void deleteBatch(List<Long> ids);
    
    /**
     * 更新订单状态
     * @param id 订单ID
     * @param status 新状态
     * @return 是否更新成功
     */
    boolean updateOrderStatus(Long id, OrderStatus status);
    
    /**
     * 根据供应商ID查询订单列表
     * @param supplierId 供应商ID
     * @return 订单列表
     */
    List<PurchaseOrder> findBySupplierId(Long supplierId);
    
    /**
     * 根据状态统计订单数量
     * @param status 订单状态
     * @return 订单数量
     */
    long countByStatus(OrderStatus status);
    
    /**
     * 查找需要审批的订单
     * @param approverId 审批人ID
     * @return 需要审批的订单列表
     */
    List<PurchaseOrder> findOrdersPendingApproval(Long approverId);
    
    /**
     * 查找超时的订单
     * @return 超时订单列表
     */
    List<PurchaseOrder> findExpiredOrders();
    
    /**
     * 并发安全地锁定订单进行更新
     * @param id 订单ID
     * @param lockTimeout 锁定超时时间（毫秒）
     * @return 锁定的订单对象，如果锁定失败则返回空
     */
    Optional<PurchaseOrder> lockForUpdate(Long id, long lockTimeout);
    
    /**
     * 解锁订单
     * @param id 订单ID
     */
    void unlock(Long id);
    
    /**
     * 添加订单项
     * @param orderId 订单ID
     * @param item 订单项
     * @return 添加后的订单项
     */
    PurchaseOrderItem addOrderItem(Long orderId, PurchaseOrderItem item);
    
    /**
     * 批量添加订单项
     * @param orderId 订单ID
     * @param items 订单项列表
     */
    void addOrderItems(Long orderId, List<PurchaseOrderItem> items);
    
    /**
     * 删除订单项
     * @param orderId 订单ID
     * @param itemId 订单项ID
     */
    void removeOrderItem(Long orderId, Long itemId);
    
    /**
     * 异步创建订单
     * @param order 订单对象
     * @return 异步创建结果
     */
    CompletableFuture<PurchaseOrder> createAsync(PurchaseOrder order);
    
    /**
     * 异步查询订单
     * @param id 订单ID
     * @return 异步查询结果
     */
    CompletableFuture<Optional<PurchaseOrder>> findByIdAsync(Long id);
    
    /**
     * 提交事务
     */
    void commit();
    
    /**
     * 回滚事务
     */
    void rollback();
    
    /**
     * 开始事务
     */
    void beginTransaction();
    
    /**
     * 批量保存订单
     * @param orders 订单列表
     * @return 保存的订单数量
     */
    int batchSave(List<PurchaseOrder> orders);
    
    /**
     * 高级搜索功能
     * @param searchCriteria 搜索条件
     * @return 搜索结果
     */
    List<PurchaseOrder> advancedSearch(AdvancedSearchCriteria searchCriteria);
    
    /**
     * 按金额范围查询订单
     * @param minAmount 最小金额
     * @param maxAmount 最大金额
     * @return 符合条件的订单列表
     */
    List<PurchaseOrder> findByAmountRange(double minAmount, double maxAmount);
    
    /**
     * 查询最近创建的订单
     * @param limit 限制数量
     * @return 最近创建的订单列表
     */
    List<PurchaseOrder> findRecentlyCreated(int limit);
    
    /**
     * 查询订单统计信息
     * @return 统计信息对象
     */
    OrderStatistics getOrderStatistics();
    
    /**
     * 分页结果类
     */
    class PageResult<T> {
        private List<T> content;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        
        // 构造函数、getter和setter方法
        public PageResult(List<T> content, int page, int size, long totalElements) {
            this.content = content;
            this.page = page;
            this.size = size;
            this.totalElements = totalElements;
            this.totalPages = (int) Math.ceil((double) totalElements / size);
        }
        
        public List<T> getContent() {
            return content;
        }
        
        public void setContent(List<T> content) {
            this.content = content;
        }
        
        public int getPage() {
            return page;
        }
        
        public void setPage(int page) {
            this.page = page;
        }
        
        public int getSize() {
            return size;
        }
        
        public void setSize(int size) {
            this.size = size;
        }
        
        public long getTotalElements() {
            return totalElements;
        }
        
        public void setTotalElements(long totalElements) {
            this.totalElements = totalElements;
        }
        
        public int getTotalPages() {
            return totalPages;
        }
        
        public void setTotalPages(int totalPages) {
            this.totalPages = totalPages;
        }
        
        public boolean hasNext() {
            return page < totalPages;
        }
        
        public boolean hasPrevious() {
            return page > 1;
        }
    }
    
    /**
     * 高级搜索条件类
     */
    class AdvancedSearchCriteria {
        private String keyword;
        private List<OrderStatus> statusList;
        private Long supplierId;
        private Long departmentId;
        private String creationDateFrom;
        private String creationDateTo;
        private Double minTotalAmount;
        private Double maxTotalAmount;
        private List<String> sortFields;
        private String sortOrder;
        
        // 构造函数、getter和setter方法
        public String getKeyword() {
            return keyword;
        }
        
        public void setKeyword(String keyword) {
            this.keyword = keyword;
        }
        
        public List<OrderStatus> getStatusList() {
            return statusList;
        }
        
        public void setStatusList(List<OrderStatus> statusList) {
            this.statusList = statusList;
        }
        
        public Long getSupplierId() {
            return supplierId;
        }
        
        public void setSupplierId(Long supplierId) {
            this.supplierId = supplierId;
        }
        
        public Long getDepartmentId() {
            return departmentId;
        }
        
        public void setDepartmentId(Long departmentId) {
            this.departmentId = departmentId;
        }
        
        public String getCreationDateFrom() {
            return creationDateFrom;
        }
        
        public void setCreationDateFrom(String creationDateFrom) {
            this.creationDateFrom = creationDateFrom;
        }
        
        public String getCreationDateTo() {
            return creationDateTo;
        }
        
        public void setCreationDateTo(String creationDateTo) {
            this.creationDateTo = creationDateTo;
        }
        
        public Double getMinTotalAmount() {
            return minTotalAmount;
        }
        
        public void setMinTotalAmount(Double minTotalAmount) {
            this.minTotalAmount = minTotalAmount;
        }
        
        public Double getMaxTotalAmount() {
            return maxTotalAmount;
        }
        
        public void setMaxTotalAmount(Double maxTotalAmount) {
            this.maxTotalAmount = maxTotalAmount;
        }
        
        public List<String> getSortFields() {
            return sortFields;
        }
        
        public void setSortFields(List<String> sortFields) {
            this.sortFields = sortFields;
        }
        
        public String getSortOrder() {
            return sortOrder;
        }
        
        public void setSortOrder(String sortOrder) {
            this.sortOrder = sortOrder;
        }
    }
    
    /**
     * 订单统计信息类
     */
    class OrderStatistics {
        private long totalOrders;
        private long pendingApproval;
        private long approved;
        private long rejected;
        private long completed;
        private double totalAmount;
        private double averageOrderValue;
        
        // 构造函数、getter和setter方法
        public OrderStatistics() {
        }
        
        public OrderStatistics(long totalOrders, long pendingApproval, long approved, 
                             long rejected, long completed, double totalAmount) {
            this.totalOrders = totalOrders;
            this.pendingApproval = pendingApproval;
            this.approved = approved;
            this.rejected = rejected;
            this.completed = completed;
            this.totalAmount = totalAmount;
            this.averageOrderValue = totalOrders > 0 ? totalAmount / totalOrders : 0;
        }
        
        public long getTotalOrders() {
            return totalOrders;
        }
        
        public void setTotalOrders(long totalOrders) {
            this.totalOrders = totalOrders;
        }
        
        public long getPendingApproval() {
            return pendingApproval;
        }
        
        public void setPendingApproval(long pendingApproval) {
            this.pendingApproval = pendingApproval;
        }
        
        public long getApproved() {
            return approved;
        }
        
        public void setApproved(long approved) {
            this.approved = approved;
        }
        
        public long getRejected() {
            return rejected;
        }
        
        public void setRejected(long rejected) {
            this.rejected = rejected;
        }
        
        public long getCompleted() {
            return completed;
        }
        
        public void setCompleted(long completed) {
            this.completed = completed;
        }
        
        public double getTotalAmount() {
            return totalAmount;
        }
        
        public void setTotalAmount(double totalAmount) {
            this.totalAmount = totalAmount;
        }
        
        public double getAverageOrderValue() {
            return averageOrderValue;
        }
        
        public void setAverageOrderValue(double averageOrderValue) {
            this.averageOrderValue = averageOrderValue;
        }
    }
}