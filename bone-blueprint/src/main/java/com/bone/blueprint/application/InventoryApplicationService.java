package com.bone.blueprint.application;

import com.bone.blueprint.application.port.out.TenantPort;
import com.bone.blueprint.application.query.dto.InventoryDto;
import com.bone.blueprint.common.BlueprintErrorCodes;
import com.bone.blueprint.common.BlueprintErrors;
import com.bone.blueprint.domain.model.inventory.Inventory;
import com.bone.blueprint.domain.repository.InventoryRepository;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 库存应用层门面 —— 多渠道共享库存的唯一真源。
 *
 * <p><b>为何库存必须落在交易域</b>：原本下单的库存校验走 {@code InventoryGateway} 的 Mock 实现（恒放行），
 * 那是占位实现，超卖在联调环境永远测不出来。真实多渠道场景下四个渠道<strong>共享同一份实物库存</strong>： 淘宝卖掉一件，抖音的可用量必须同步减少，否则各自按自己的数卖，必然超卖。
 * 因此库存必须成为交易域内部的事实真源，不能再是「远程调用别的系统」。
 *
 * <p><b>并发模型</b>：全部写操作走 {@code @Version} 乐观锁（架构硬约束禁 {@code SELECT ... FOR UPDATE}）。 预留失败（库存不足）转成
 * {@code BP_INVENTORY_INSUFFICIENT} 业务码——超卖是业务问题，不是系统故障， 报成 5xx 会污染 SLO 口径。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryApplicationService {

  private final InventoryRepository inventoryRepository;
  private final TenantPort tenantProvider;

  /**
   * 库存 → 渠道广播（Outbox 异步）。
   *
   * <p>注入应用服务而非广播端口：要不要广播、广播给哪些渠道（商品在该渠道是否 ONLINE）是<strong>业务规则</strong>， 集中在 {@link
   * ChannelBroadcastApplicationService} 判定；本类只负责在<strong>可售量变化</strong>后触发一次。
   */
  private final ChannelBroadcastApplicationService channelBroadcastApplicationService;

  @Transactional(readOnly = true)
  public PageResult<InventoryDto> page(Long productId, Boolean lowStockOnly, int page, int size) {
    long tenantId = tenantProvider.currentTenantId();
    PageResult<Inventory> result =
        inventoryRepository.findPage(tenantId, productId, lowStockOnly, page, size);
    List<InventoryDto> records =
        result.getRecords() == null
            ? List.of()
            : result.getRecords().stream().map(InventoryDto::from).toList();
    return PageResult.of(records, result.getTotal(), result.getPage(), result.getSize());
  }

  @Transactional(readOnly = true)
  public InventoryDto detail(Long productId, String warehouseCode) {
    long tenantId = tenantProvider.currentTenantId();
    return InventoryDto.from(
        inventoryRepository.findByProduct(tenantId, productId, defaultWarehouse(warehouseCode)));
  }

  /**
   * 建库存 / 入库。已存在则累加可用量（采购到货），不存在则创建。
   *
   * @param delta 入库数量，必须为正
   */
  @Transactional
  public InventoryDto receive(Long productId, String productName, String warehouseCode, int delta) {
    long tenantId = tenantProvider.currentTenantId();
    String warehouse = defaultWarehouse(warehouseCode);
    Inventory entity = inventoryRepository.findByProduct(tenantId, productId, warehouse);
    boolean created = entity == null;
    if (created) {
      entity =
          Inventory.create(
              DistributedIdGenerator.generateLongId(),
              tenantId,
              productId,
              productName,
              warehouse,
              0,
              0);
    }
    entity.receive(delta);
    if (productName != null && !productName.isBlank()) {
      entity.updateProductName(productName);
    }
    persist(entity, created);
    // 库存变化必须广播到已上架渠道，否则渠道侧仍是旧库存 → 超卖。
    // 现在是「同事务入队 + 中继异步投递」：入队与库存变更原子提交，投递不占本事务。
    enqueueBroadcast(productId, entity);
    log.info("库存入库 | product={} | warehouse={} | delta={}", productId, warehouse, delta);
    return InventoryDto.from(entity);
  }

  /** 扣减可用库存（报损 / 纠错），不足则抛业务异常。 */
  @Transactional
  public InventoryDto deduct(Long productId, String warehouseCode, int delta) {
    long tenantId = tenantProvider.currentTenantId();
    Inventory entity = requireInventory(tenantId, productId, warehouseCode);
    try {
      entity.deduct(delta);
    } catch (IllegalStateException ex) {
      throw BlueprintErrors.of(BlueprintErrorCodes.INVENTORY_INSUFFICIENT, ex.getMessage());
    }
    inventoryRepository.update(entity);
    enqueueBroadcast(productId, entity);
    return InventoryDto.from(entity);
  }

  /** 设置安全库存阈值。 */
  @Transactional
  public InventoryDto setSafetyStock(Long productId, String warehouseCode, int safetyStock) {
    long tenantId = tenantProvider.currentTenantId();
    Inventory entity = requireInventory(tenantId, productId, warehouseCode);
    entity.updateSafetyStock(safetyStock);
    inventoryRepository.update(entity);
    return InventoryDto.from(entity);
  }

  /**
   * 预留库存（下单占用）。
   *
   * <p>供订单创建链路调用；库存不足抛 {@code BP_INVENTORY_INSUFFICIENT}。
   *
   * @return 预留后的库存视图
   */
  @Transactional
  public InventoryDto reserve(Long productId, String warehouseCode, int quantity) {
    long tenantId = tenantProvider.currentTenantId();
    Inventory entity = requireInventory(tenantId, productId, warehouseCode);
    try {
      entity.reserve(quantity);
    } catch (IllegalStateException ex) {
      throw BlueprintErrors.of(BlueprintErrorCodes.INVENTORY_INSUFFICIENT, ex.getMessage());
    }
    inventoryRepository.update(entity);
    enqueueBroadcast(productId, entity);
    return InventoryDto.from(entity);
  }

  /** 确认出库（预留 → 实际出库）。不广播：可售量在 reserve 时已扣减，此处只减预留量。 */
  @Transactional
  public InventoryDto confirm(Long productId, String warehouseCode, int quantity) {
    long tenantId = tenantProvider.currentTenantId();
    Inventory entity = requireInventory(tenantId, productId, warehouseCode);
    entity.confirm(quantity);
    inventoryRepository.update(entity);
    return InventoryDto.from(entity);
  }

  /** 释放预留（取消订单 / 超时关单）：可售量回升，必须广播，否则渠道侧仍显示售罄。 */
  @Transactional
  public InventoryDto release(Long productId, String warehouseCode, int quantity) {
    long tenantId = tenantProvider.currentTenantId();
    Inventory entity = requireInventory(tenantId, productId, warehouseCode);
    entity.release(quantity);
    inventoryRepository.update(entity);
    enqueueBroadcast(productId, entity);
    return InventoryDto.from(entity);
  }

  /**
   * 把当前可售量入队广播到已上架渠道（与库存变更同事务）。
   *
   * <p>传可售量 {@code availableQty} 而非实物总量：预留时已从 available 扣减，渠道卖的是可售量。传总量会让预留中的订单继续在渠道可买 → 超卖。
   *
   * <p><b>为什么失败就让整个库存操作失败（不吞异常）</b>：本方法在库存事务内写 Outbox。一旦写入失败，事务已被标记 rollback-only，此时 catch
   * 掉异常只会在提交阶段抛 {@code UnexpectedRollbackException}——一个把真正原因完全掩盖的 500， 比直接失败更难排查。而 Outbox
   * 的全部价值就在于「库存变更」与「待广播」同生共死：库存没提交，渠道自然也不用同步。 真正需要「不因广播失败而丢库存」的场景，应由中继重试与人工重试覆盖，而不是在这里吞异常。
   */
  private void enqueueBroadcast(Long productId, Inventory entity) {
    channelBroadcastApplicationService.enqueueForProduct(productId, entity.getAvailableQty());
  }

  /** 同步库存到渠道（由调用方在库存变更后触发）。 */
  @Transactional(readOnly = true)
  public int availableQuantity(Long productId, String warehouseCode) {
    long tenantId = tenantProvider.currentTenantId();
    Inventory entity =
        inventoryRepository.findByProduct(tenantId, productId, defaultWarehouse(warehouseCode));
    return entity == null ? 0 : entity.getAvailableQty();
  }

  private Inventory requireInventory(long tenantId, Long productId, String warehouseCode) {
    Inventory entity =
        inventoryRepository.findByProduct(tenantId, productId, defaultWarehouse(warehouseCode));
    if (entity == null) {
      throw BlueprintErrors.of(
          BlueprintErrorCodes.INVENTORY_NOT_FOUND,
          productId + "@" + defaultWarehouse(warehouseCode));
    }
    return entity;
  }

  private void persist(Inventory entity, boolean created) {
    if (created) {
      inventoryRepository.insert(entity);
    } else {
      inventoryRepository.update(entity);
    }
  }

  private static String defaultWarehouse(String warehouseCode) {
    return warehouseCode == null || warehouseCode.isBlank() ? "DEFAULT" : warehouseCode.trim();
  }
}
