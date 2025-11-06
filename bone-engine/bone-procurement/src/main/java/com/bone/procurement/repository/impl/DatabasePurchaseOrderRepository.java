package com.bone.procurement.repository.impl;

import com.bone.procurement.entity.PurchaseOrder;
import com.bone.procurement.entity.PurchaseOrderItem;
import com.bone.procurement.model.OrderQueryCriteria;
import com.bone.procurement.model.OrderStatus;
import com.bone.procurement.repository.PurchaseOrderRepository;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 采购订单仓储的数据库实现类
 * 基于Hibernate/JPA提供持久化支持
 */
@Repository
public class DatabasePurchaseOrderRepository implements PurchaseOrderRepository {

    private static final Logger logger = LoggerFactory.getLogger(DatabasePurchaseOrderRepository.class);
    private final SessionFactory sessionFactory;
    private final ExecutorService executorService;
    private final Map<Long, Long> lockMap; // 简单的分布式锁实现
    
    @Autowired
    public DatabasePurchaseOrderRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
        this.executorService = Executors.newFixedThreadPool(10);
        this.lockMap = new ConcurrentHashMap<>();
    }
    
    @Override
    public PurchaseOrder save(PurchaseOrder order) {
        Session session = sessionFactory.getCurrentSession();
        Transaction transaction = session.beginTransaction();
        try {
            if (order.getId() == null) {
                session.save(order);
            } else {
                session.update(order);
            }
            transaction.commit();
            return order;
        } catch (Exception e) {
            transaction.rollback();
            logger.error("Failed to save purchase order", e);
            throw new RuntimeException("Failed to save purchase order", e);
        }
    }
    
    @Override
    public PurchaseOrder findById(Long id) {
        Session session = sessionFactory.getCurrentSession();
        return session.get(PurchaseOrder.class, id);
    }
    
    @Override
    public void deleteById(Long id) {
        Session session = sessionFactory.getCurrentSession();
        Transaction transaction = session.beginTransaction();
        try {
            PurchaseOrder order = session.get(PurchaseOrder.class, id);
            if (order != null) {
                session.delete(order);
            }
            transaction.commit();
        } catch (Exception e) {
            transaction.rollback();
            logger.error("Failed to delete purchase order with id: {}", id, e);
            throw new RuntimeException("Failed to delete purchase order", e);
        }
    }
    
    @Override
    public PurchaseOrder create(PurchaseOrder order) {
        return save(order);
    }
    
    @Override
    public Optional<PurchaseOrder> findByIdOptional(Long id) {
        return Optional.ofNullable(findById(id));
    }
    
    @Override
    public List<PurchaseOrder> findByCriteria(OrderQueryCriteria criteria) {
        Session session = sessionFactory.getCurrentSession();
        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaQuery<PurchaseOrder> query = builder.createQuery(PurchaseOrder.class);
        Root<PurchaseOrder> root = query.from(PurchaseOrder.class);
        
        List<Predicate> predicates = buildPredicates(criteria, builder, root);
        
        if (!predicates.isEmpty()) {
            query.where(builder.and(predicates.toArray(new Predicate[0])));
        }
        
        // 添加排序
        if (criteria.getSortField() != null) {
            if (criteria.isAscending()) {
                query.orderBy(builder.asc(root.get(criteria.getSortField())));
            } else {
                query.orderBy(builder.desc(root.get(criteria.getSortField())));
            }
        }
        
        return session.createQuery(query).list();
    }
    
    @Override
    public PageResult<PurchaseOrder> findByCriteriaWithPagination(OrderQueryCriteria criteria, int page, int size) {
        Session session = sessionFactory.getCurrentSession();
        CriteriaBuilder builder = session.getCriteriaBuilder();
        
        // 计算总数
        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<PurchaseOrder> countRoot = countQuery.from(PurchaseOrder.class);
        List<Predicate> countPredicates = buildPredicates(criteria, builder, countRoot);
        if (!countPredicates.isEmpty()) {
            countQuery.where(builder.and(countPredicates.toArray(new Predicate[0])));
        }
        countQuery.select(builder.count(countRoot));
        long totalElements = session.createQuery(countQuery).uniqueResult();
        
        // 查询分页数据
        CriteriaQuery<PurchaseOrder> dataQuery = builder.createQuery(PurchaseOrder.class);
        Root<PurchaseOrder> dataRoot = dataQuery.from(PurchaseOrder.class);
        List<Predicate> dataPredicates = buildPredicates(criteria, builder, dataRoot);
        if (!dataPredicates.isEmpty()) {
            dataQuery.where(builder.and(dataPredicates.toArray(new Predicate[0])));
        }
        
        // 添加排序
        if (criteria.getSortField() != null) {
            if (criteria.isAscending()) {
                dataQuery.orderBy(builder.asc(dataRoot.get(criteria.getSortField())));
            } else {
                dataQuery.orderBy(builder.desc(dataRoot.get(criteria.getSortField())));
            }
        }
        
        List<PurchaseOrder> content = session.createQuery(dataQuery)
                .setFirstResult((page - 1) * size)
                .setMaxResults(size)
                .list();
        
        return new PageResult<>(content, page, size, totalElements);
    }
    
    @Override
    public PurchaseOrder update(PurchaseOrder order) {
        return save(order);
    }
    
    @Override
    public void delete(Long id) {
        deleteById(id);
    }
    
    @Override
    public void deleteBatch(List<Long> ids) {
        Session session = sessionFactory.getCurrentSession();
        Transaction transaction = session.beginTransaction();
        try {
            Query<?> query = session.createQuery("DELETE FROM PurchaseOrder WHERE id IN (:ids)");
            query.setParameterList("ids", ids);
            query.executeUpdate();
            transaction.commit();
        } catch (Exception e) {
            transaction.rollback();
            logger.error("Failed to batch delete purchase orders", e);
            throw new RuntimeException("Failed to batch delete purchase orders", e);
        }
    }
    
    @Override
    public boolean updateOrderStatus(Long id, OrderStatus status) {
        Session session = sessionFactory.getCurrentSession();
        Transaction transaction = session.beginTransaction();
        try {
            Query<?> query = session.createQuery("UPDATE PurchaseOrder SET status = :status WHERE id = :id");
            query.setParameter("status", status);
            query.setParameter("id", id);
            int updated = query.executeUpdate();
            transaction.commit();
            return updated > 0;
        } catch (Exception e) {
            transaction.rollback();
            logger.error("Failed to update order status for id: {}", id, e);
            return false;
        }
    }
    
    @Override
    public List<PurchaseOrder> findBySupplierId(Long supplierId) {
        Session session = sessionFactory.getCurrentSession();
        Query<PurchaseOrder> query = session.createQuery("FROM PurchaseOrder WHERE supplierId = :supplierId", PurchaseOrder.class);
        query.setParameter("supplierId", supplierId);
        return query.list();
    }
    
    @Override
    public long countByStatus(OrderStatus status) {
        Session session = sessionFactory.getCurrentSession();
        Query<Long> query = session.createQuery("SELECT COUNT(*) FROM PurchaseOrder WHERE status = :status", Long.class);
        query.setParameter("status", status);
        return query.uniqueResult();
    }
    
    @Override
    public List<PurchaseOrder> findOrdersPendingApproval(Long approverId) {
        Session session = sessionFactory.getCurrentSession();
        Query<PurchaseOrder> query = session.createQuery(
                "FROM PurchaseOrder WHERE status = :status AND approverId = :approverId", PurchaseOrder.class);
        query.setParameter("status", OrderStatus.PENDING_APPROVAL);
        query.setParameter("approverId", approverId);
        return query.list();
    }
    
    @Override
    public List<PurchaseOrder> findExpiredOrders() {
        Session session = sessionFactory.getCurrentSession();
        // 查找超时的订单，假设超时时间为当前时间减去24小时
        Date expirationDate = new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000);
        Query<PurchaseOrder> query = session.createQuery(
                "FROM PurchaseOrder WHERE status = :status AND creationDate < :expirationDate", PurchaseOrder.class);
        query.setParameter("status", OrderStatus.PENDING_APPROVAL);
        query.setParameter("expirationDate", expirationDate);
        return query.list();
    }
    
    @Override
    public Optional<PurchaseOrder> lockForUpdate(Long id, long lockTimeout) {
        long currentTime = System.currentTimeMillis();
        Long lockTime = lockMap.putIfAbsent(id, currentTime);
        
        // 检查锁是否已存在且未超时
        if (lockTime != null && (currentTime - lockTime) < lockTimeout) {
            return Optional.empty(); // 锁被其他线程持有
        }
        
        // 如果锁已超时，更新锁时间
        if (lockTime != null) {
            lockMap.put(id, currentTime);
        }
        
        // 使用数据库行级锁
        Session session = sessionFactory.getCurrentSession();
        Query<PurchaseOrder> query = session.createQuery(
                "FROM PurchaseOrder WHERE id = :id", PurchaseOrder.class);
        query.setParameter("id", id);
        query.setLockMode("p", org.hibernate.LockMode.PESSIMISTIC_WRITE);
        
        PurchaseOrder order = query.uniqueResult();
        return Optional.ofNullable(order);
    }
    
    @Override
    public void unlock(Long id) {
        lockMap.remove(id);
    }
    
    @Override
    public PurchaseOrderItem addOrderItem(Long orderId, PurchaseOrderItem item) {
        Session session = sessionFactory.getCurrentSession();
        Transaction transaction = session.beginTransaction();
        try {
            PurchaseOrder order = session.get(PurchaseOrder.class, orderId);
            if (order != null) {
                item.setOrder(order);
                session.save(item);
                transaction.commit();
                return item;
            }
            transaction.rollback();
            throw new IllegalArgumentException("Order not found: " + orderId);
        } catch (Exception e) {
            transaction.rollback();
            logger.error("Failed to add order item to order: {}", orderId, e);
            throw new RuntimeException("Failed to add order item", e);
        }
    }
    
    @Override
    public void addOrderItems(Long orderId, List<PurchaseOrderItem> items) {
        Session session = sessionFactory.getCurrentSession();
        Transaction transaction = session.beginTransaction();
        try {
            PurchaseOrder order = session.get(PurchaseOrder.class, orderId);
            if (order != null) {
                for (PurchaseOrderItem item : items) {
                    item.setOrder(order);
                    session.save(item);
                }
                transaction.commit();
            } else {
                transaction.rollback();
                throw new IllegalArgumentException("Order not found: " + orderId);
            }
        } catch (Exception e) {
            transaction.rollback();
            logger.error("Failed to add order items to order: {}", orderId, e);
            throw new RuntimeException("Failed to add order items", e);
        }
    }
    
    @Override
    public void removeOrderItem(Long orderId, Long itemId) {
        Session session = sessionFactory.getCurrentSession();
        Transaction transaction = session.beginTransaction();
        try {
            Query<?> query = session.createQuery(
                    "DELETE FROM PurchaseOrderItem WHERE id = :itemId AND order.id = :orderId");
            query.setParameter("itemId", itemId);
            query.setParameter("orderId", orderId);
            query.executeUpdate();
            transaction.commit();
        } catch (Exception e) {
            transaction.rollback();
            logger.error("Failed to remove order item: {} from order: {}", itemId, orderId, e);
            throw new RuntimeException("Failed to remove order item", e);
        }
    }
    
    @Override
    public CompletableFuture<PurchaseOrder> createAsync(PurchaseOrder order) {
        return CompletableFuture.supplyAsync(() -> create(order), executorService);
    }
    
    @Override
    public CompletableFuture<Optional<PurchaseOrder>> findByIdAsync(Long id) {
        return CompletableFuture.supplyAsync(() -> findByIdOptional(id), executorService);
    }
    
    @Override
    public void commit() {
        // 在Spring事务管理中，事务提交通常由容器管理
        // 这里提供接口以便特殊场景使用
        Session session = sessionFactory.getCurrentSession();
        if (session.getTransaction().isActive()) {
            session.getTransaction().commit();
        }
    }
    
    @Override
    public void rollback() {
        // 在Spring事务管理中，事务回滚通常由容器管理
        // 这里提供接口以便特殊场景使用
        Session session = sessionFactory.getCurrentSession();
        if (session.getTransaction().isActive()) {
            session.getTransaction().rollback();
        }
    }
    
    @Override
    public void beginTransaction() {
        // 在Spring事务管理中，事务开始通常由容器管理
        // 这里提供接口以便特殊场景使用
        Session session = sessionFactory.getCurrentSession();
        if (!session.getTransaction().isActive()) {
            session.beginTransaction();
        }
    }
    
    @Override
    public int batchSave(List<PurchaseOrder> orders) {
        Session session = sessionFactory.getCurrentSession();
        Transaction transaction = session.beginTransaction();
        try {
            for (int i = 0; i < orders.size(); i++) {
                session.saveOrUpdate(orders.get(i));
                // 每100条刷新一次，避免内存占用过大
                if (i > 0 && i % 100 == 0) {
                    session.flush();
                    session.clear();
                }
            }
            transaction.commit();
            return orders.size();
        } catch (Exception e) {
            transaction.rollback();
            logger.error("Failed to batch save purchase orders", e);
            throw new RuntimeException("Failed to batch save purchase orders", e);
        }
    }
    
    @Override
    public List<PurchaseOrder> advancedSearch(AdvancedSearchCriteria searchCriteria) {
        Session session = sessionFactory.getCurrentSession();
        CriteriaBuilder builder = session.getCriteriaBuilder();
        CriteriaQuery<PurchaseOrder> query = builder.createQuery(PurchaseOrder.class);
        Root<PurchaseOrder> root = query.from(PurchaseOrder.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        // 关键词搜索
        if (searchCriteria.getKeyword() != null && !searchCriteria.getKeyword().isEmpty()) {
            String keyword = "%" + searchCriteria.getKeyword() + "%";
            predicates.add(builder.or(
                    builder.like(root.get("orderNumber"), keyword),
                    builder.like(root.get("description"), keyword)
            ));
        }
        
        // 状态列表
        if (searchCriteria.getStatusList() != null && !searchCriteria.getStatusList().isEmpty()) {
            predicates.add(root.get("status").in(searchCriteria.getStatusList()));
        }
        
        // 供应商ID
        if (searchCriteria.getSupplierId() != null) {
            predicates.add(builder.equal(root.get("supplierId"), searchCriteria.getSupplierId()));
        }
        
        // 部门ID
        if (searchCriteria.getDepartmentId() != null) {
            predicates.add(builder.equal(root.get("departmentId"), searchCriteria.getDepartmentId()));
        }
        
        // 日期范围
        if (searchCriteria.getCreationDateFrom() != null) {
            predicates.add(builder.greaterThanOrEqualTo(root.get("creationDate"), 
                    searchCriteria.getCreationDateFrom()));
        }
        if (searchCriteria.getCreationDateTo() != null) {
            predicates.add(builder.lessThanOrEqualTo(root.get("creationDate"), 
                    searchCriteria.getCreationDateTo()));
        }
        
        // 金额范围
        if (searchCriteria.getMinTotalAmount() != null) {
            predicates.add(builder.greaterThanOrEqualTo(root.get("totalAmount"), 
                    searchCriteria.getMinTotalAmount()));
        }
        if (searchCriteria.getMaxTotalAmount() != null) {
            predicates.add(builder.lessThanOrEqualTo(root.get("totalAmount"), 
                    searchCriteria.getMaxTotalAmount()));
        }
        
        // 添加条件
        if (!predicates.isEmpty()) {
            query.where(builder.and(predicates.toArray(new Predicate[0])));
        }
        
        // 添加排序
        if (searchCriteria.getSortFields() != null && !searchCriteria.getSortFields().isEmpty()) {
            List<javax.persistence.criteria.Order> orders = new ArrayList<>();
            boolean ascending = "ASC".equalsIgnoreCase(searchCriteria.getSortOrder());
            
            for (String field : searchCriteria.getSortFields()) {
                if (ascending) {
                    orders.add(builder.asc(root.get(field)));
                } else {
                    orders.add(builder.desc(root.get(field)));
                }
            }
            
            query.orderBy(orders);
        }
        
        return session.createQuery(query).list();
    }
    
    @Override
    public List<PurchaseOrder> findByAmountRange(double minAmount, double maxAmount) {
        Session session = sessionFactory.getCurrentSession();
        Query<PurchaseOrder> query = session.createQuery(
                "FROM PurchaseOrder WHERE totalAmount BETWEEN :minAmount AND :maxAmount", PurchaseOrder.class);
        query.setParameter("minAmount", minAmount);
        query.setParameter("maxAmount", maxAmount);
        return query.list();
    }
    
    @Override
    public List<PurchaseOrder> findRecentlyCreated(int limit) {
        Session session = sessionFactory.getCurrentSession();
        Query<PurchaseOrder> query = session.createQuery(
                "FROM PurchaseOrder ORDER BY creationDate DESC", PurchaseOrder.class);
        query.setMaxResults(limit);
        return query.list();
    }
    
    @Override
    public OrderStatistics getOrderStatistics() {
        Session session = sessionFactory.getCurrentSession();
        
        // 查询总数
        Long totalOrders = session.createQuery("SELECT COUNT(*) FROM PurchaseOrder", Long.class).uniqueResult();
        
        // 查询各状态数量
        Long pendingApproval = countByStatus(OrderStatus.PENDING_APPROVAL);
        Long approved = countByStatus(OrderStatus.APPROVED);
        Long rejected = countByStatus(OrderStatus.REJECTED);
        Long completed = countByStatus(OrderStatus.COMPLETED);
        
        // 查询总金额
        Double totalAmount = session.createQuery(
                "SELECT SUM(totalAmount) FROM PurchaseOrder", Double.class).uniqueResult();
        if (totalAmount == null) {
            totalAmount = 0.0;
        }
        
        return new OrderStatistics(
                totalOrders, pendingApproval, approved, rejected, completed, totalAmount);
    }
    
    // 辅助方法：构建查询条件
    private List<Predicate> buildPredicates(OrderQueryCriteria criteria, 
                                          CriteriaBuilder builder, 
                                          Root<PurchaseOrder> root) {
        List<Predicate> predicates = new ArrayList<>();
        
        if (criteria.getStatus() != null) {
            predicates.add(builder.equal(root.get("status"), criteria.getStatus()));
        }
        
        if (criteria.getSupplierId() != null) {
            predicates.add(builder.equal(root.get("supplierId"), criteria.getSupplierId()));
        }
        
        if (criteria.getDepartmentId() != null) {
            predicates.add(builder.equal(root.get("departmentId"), criteria.getDepartmentId()));
        }
        
        if (criteria.getCreatedBy() != null) {
            predicates.add(builder.equal(root.get("createdBy"), criteria.getCreatedBy()));
        }
        
        if (criteria.getMinTotalAmount() != null) {
            predicates.add(builder.greaterThanOrEqualTo(root.get("totalAmount"), criteria.getMinTotalAmount()));
        }
        
        if (criteria.getMaxTotalAmount() != null) {
            predicates.add(builder.lessThanOrEqualTo(root.get("totalAmount"), criteria.getMaxTotalAmount()));
        }
        
        if (criteria.getFromDate() != null) {
            predicates.add(builder.greaterThanOrEqualTo(root.get("creationDate"), criteria.getFromDate()));
        }
        
        if (criteria.getToDate() != null) {
            predicates.add(builder.lessThanOrEqualTo(root.get("creationDate"), criteria.getToDate()));
        }
        
        return predicates;
    }
}