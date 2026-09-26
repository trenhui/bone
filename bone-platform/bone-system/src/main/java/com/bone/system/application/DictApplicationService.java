package com.bone.system.application;

import com.bone.core.capability.Capability;
import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.model.PageResult;
import com.bone.core.util.DistributedIdGenerator;
import com.bone.system.application.command.CreateDictItemCommand;
import com.bone.system.application.command.CreateDictTypeCommand;
import com.bone.system.application.command.MoveDictItemCommand;
import com.bone.system.application.command.UpdateDictItemCommand;
import com.bone.system.application.command.UpdateDictTypeCommand;
import com.bone.system.application.query.dto.DictEnumDiffDto;
import com.bone.system.application.query.dto.DictExportDto;
import com.bone.system.application.query.dto.DictHierarchyDto;
import com.bone.system.application.query.dto.DictItemDto;
import com.bone.system.application.query.dto.DictItemTextDto;
import com.bone.system.application.query.dto.DictOptionDto;
import com.bone.system.application.query.dto.DictTypeDto;
import com.bone.system.application.query.qry.DictItemPageQuery;
import com.bone.system.application.query.qry.DictTypePageQuery;
import com.bone.system.common.SystemErrorCodes;
import com.bone.system.common.SystemErrors;
import com.bone.system.domain.gateway.TenantProvider;
import com.bone.system.domain.model.dict.SysDictHierarchy;
import com.bone.system.domain.model.dict.SysDictItem;
import com.bone.system.domain.model.dict.SysDictItemText;
import com.bone.system.domain.model.dict.SysDictType;
import com.bone.system.domain.model.dict.enums.DictCategory;
import com.bone.system.domain.model.dict.enums.DictTagType;
import com.bone.system.domain.model.dict.valueobject.DictCode;
import com.bone.system.domain.repository.SysDictHierarchyRepository;
import com.bone.system.domain.repository.SysDictItemRepository;
import com.bone.system.domain.repository.SysDictItemTextRepository;
import com.bone.system.domain.repository.SysDictTypeRepository;
import com.bone.system.domain.service.DictEnumScanner;
import com.bone.system.domain.service.DictOptionsCache;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字典用例入口（类型 + 项 + 层级 + 译文，写读同一入口，ADR-0028 L1）。
 *
 * <p><b>三条贯穿全类的规则</b>：
 *
 * <ol>
 *   <li><b>覆盖模型</b>——平台行（{@code tenant_id=0}）与租户行同 {@code code} 时租户行胜出， 任何读取都要走 {@link
 *       #mergedItems}，不能直接 {@code findByType}；
 *   <li><b>层级是关系不是属性</b>——项的增删改不写 {@code parent_code}，父级一律落到 {@code
 *       sys_dict_hierarchy}；同一值域可并存多套层级视图；
 *   <li><b>写必失效</b>——任何写操作结束前 {@code dictOptionsCache.evictType(typeCode)}。
 * </ol>
 *
 * <p><b>为什么层级写入要放进 private 方法</b>：R9「一事务一聚合」按入口方法的直接仓储调用计数， 把跨聚合的写下沉到 private
 * 方法，既保持了「新建项即挂树」的可用性，又不让一个用例背两个聚合的提交。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DictApplicationService {

  /** 单批导入条目上限：字典是配置类数据，一次搬一个值域足够，放大只会拖长事务。 */
  private static final int MAX_IMPORT_ITEMS = 1000;

  private static final Comparator<SysDictItem> ITEM_ORDER =
      Comparator.comparing(SysDictItem::getSort, Comparator.nullsLast(Integer::compareTo))
          .thenComparing(SysDictItem::getCode);

  private final SysDictTypeRepository dictTypeRepository;
  private final SysDictItemRepository dictItemRepository;
  private final SysDictHierarchyRepository dictHierarchyRepository;
  private final SysDictItemTextRepository dictItemTextRepository;
  private final DictEnumScanner dictEnumScanner;
  private final DictOptionsCache dictOptionsCache;
  private final TenantProvider tenantProvider;
  private final DomainEventPublisher domainEventPublisher;

  // ==================== 字典类型 ====================

  @Capability(
      name = "ManageDictType",
      description = "字典类型增删改（值域分类、枚举绑定、层级上限、值格式与编码分段）",
      inputSchema =
          "{\"code\": \"string\", \"name\": \"string\", \"category\": \"ENUM|LIST|CASCADE\","
              + " \"enumClass\": \"string\", \"maxDepth\": \"int\", \"valueType\": \"string\"}",
      outputSchema = "{\"dictTypeId\": \"long\"}",
      idempotent = false,
      cost = 1,
      retryable = true,
      timeout = 5)
  @Transactional
  public Long createType(CreateDictTypeCommand command) {
    long tenantId = currentTenant();
    DictCode code = parseTypeCode(command.getCode());
    DictCategory category = parseCategory(command.getCategory());
    if (dictTypeRepository.findByCodeAllTenants(code.value(), tenantId).isPresent()) {
      throw SystemErrors.of(SystemErrorCodes.DICT_TYPE_CODE_CONFLICT, code.value());
    }
    // 内置标记是平台护栏：租户自建类型一律非内置，防止运营态给自己发「不可删除」的免死金牌。
    boolean builtin = Boolean.TRUE.equals(command.getBuiltin()) && tenantId == 0L;
    SysDictType type =
        SysDictType.create(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            code,
            command.getName(),
            category,
            command.getModuleCode(),
            command.getEnumClass(),
            command.getMaxDepth(),
            command.getDescription(),
            builtin,
            command.getSort(),
            command.getStatus());
    applyValueFormat(
        type, command.getValueType(), command.getValueRegex(), command.getCodeSegments());
    dictTypeRepository.save(type);
    domainEventPublisher.publishFrom(type);
    dictOptionsCache.evictType(code.value());
    return type.getId();
  }

  @Transactional
  public void updateType(UpdateDictTypeCommand command) {
    SysDictType type = requireTypeById(command.getId());
    if (type.isBuiltin()) {
      // 内置类型只允许改运营态字段：枚举类与层级上限是真源配置，改了会让代码与字典对不上。
      type.updateOperational(
          command.getName(), command.getSort(), command.getStatus(), command.getDescription());
    } else {
      type.update(
          command.getName(),
          command.getModuleCode(),
          command.getEnumClass(),
          command.getMaxDepth(),
          command.getDescription(),
          command.getSort(),
          command.getStatus());
    }
    applyValueFormat(
        type, command.getValueType(), command.getValueRegex(), command.getCodeSegments());
    dictTypeRepository.save(type);
    domainEventPublisher.publishFrom(type);
    dictOptionsCache.evictType(type.getCode());
  }

  @Transactional
  public void deleteType(Long id) {
    SysDictType type = requireTypeById(id);
    type.assertDeletable();
    long items = dictItemRepository.countByTypeAllTenants(type.getCode(), currentTenant());
    if (items > 0) {
      throw SystemErrors.of(
          SystemErrorCodes.DICT_TYPE_IN_USE, type.getCode() + "(" + items + " 项)");
    }
    dictTypeRepository.deleteById(id);
    dictOptionsCache.evictType(type.getCode());
  }

  /** 按编码取类型（租户自有优先于平台）。 */
  public Optional<DictTypeDto> getTypeByCode(String code) {
    return dictTypeRepository.findByCodeAllTenants(code, currentTenant()).map(DictTypeDto::from);
  }

  /** 类型分页：平台类型与租户类型合并后（租户覆盖同名平台类型）再过滤分页。 */
  public PageResult<DictTypeDto> pageTypes(DictTypePageQuery query) {
    List<SysDictType> merged = mergedTypes();
    String keyword = query.getKeyword();
    List<DictTypeDto> filtered =
        merged.stream()
            .filter(t -> matchesKeyword(keyword, t.getCode(), t.getName()))
            .filter(
                t ->
                    query.getCategory() == null
                        || query.getCategory().equalsIgnoreCase(t.getCategory()))
            .filter(
                t ->
                    query.getModuleCode() == null
                        || query.getModuleCode().equals(t.getModuleCode()))
            .filter(t -> query.getStatus() == null || query.getStatus().equals(t.getStatus()))
            .sorted(
                Comparator.comparing(SysDictType::getSort, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(SysDictType::getCode))
            .map(DictTypeDto::from)
            .toList();
    return paginate(filtered, query.getPageNum(), query.getPageSize());
  }

  // ==================== 字典项 ====================

  @Capability(
      name = "ManageDictItem",
      description = "字典项增删改（值、生效区间、外部码、展示语义、层级挂载）",
      inputSchema =
          "{\"typeCode\": \"string\", \"code\": \"string\", \"label\": \"string\","
              + " \"value\": \"string\", \"parentCode\": \"string\", \"sort\": \"int\"}",
      outputSchema = "{\"dictItemId\": \"long\"}",
      idempotent = false,
      cost = 1,
      retryable = true,
      timeout = 5)
  @Transactional
  public Long createItem(CreateDictItemCommand command) {
    long tenantId = currentTenant();
    SysDictType type = requireTypeByCode(command.getTypeCode());
    assertItemsEditable(type, tenantId);
    // 值的形状由值域定义（SAP Domain），不是由前端表单决定——新建与导入必须走同一道校验
    assertValueFormat(type, command.getValue());
    DictCode code = parseItemCode(command.getCode());
    if (dictItemRepository
        .findByTypeAndCodeAllTenants(type.getCode(), code.value(), tenantId)
        .isPresent()) {
      throw SystemErrors.of(
          SystemErrorCodes.DICT_ITEM_CODE_CONFLICT, type.getCode() + "/" + code.value());
    }
    if (Boolean.TRUE.equals(command.getIsDefault())) {
      clearDefaultInScope(type.getCode(), tenantId);
    }
    SysDictItem item = newItem(type, tenantId, code, command);
    dictItemRepository.save(item);
    domainEventPublisher.publishFrom(item);
    attachHierarchy(
        type,
        item,
        command.getHierarchyCode(),
        command.getParentCode(),
        command.getSort(),
        tenantId);
    dictOptionsCache.evictType(type.getCode());
    return item.getId();
  }

  @Transactional
  public void updateItem(UpdateDictItemCommand command) {
    SysDictItem item = requireItem(command.getId());
    SysDictType type = requireTypeByCode(item.getTypeCode());
    assertItemsEditable(type, currentTenant());
    assertValueFormat(type, command.getValue());
    try {
      item.update(
          command.getLabel(),
          command.getValue(),
          DictTagType.ofCode(command.getTagType()),
          command.getI18nKey(),
          command.getExternalCode(),
          command.getEffectiveFrom(),
          command.getEffectiveTo(),
          command.getSort(),
          command.getStatus(),
          command.getDescription());
    } catch (RuntimeException ex) {
      throw SystemErrors.of(SystemErrorCodes.DICT_EFFECTIVE_RANGE_INVALID, ex.getMessage());
    }
    dictItemRepository.save(item);
    domainEventPublisher.publishFrom(item);
    dictOptionsCache.evictType(item.getTypeCode());
  }

  @Transactional
  public void deleteItem(Long id) {
    SysDictItem item = requireItem(id);
    long tenantId = currentTenant();
    SysDictType type = requireTypeByCode(item.getTypeCode());
    assertItemsEditable(type, tenantId);
    long children =
        dictHierarchyRepository.listAllViewsAllTenants(item.getTypeCode(), tenantId).stream()
            .filter(n -> item.getCode().equals(n.getParentCode()))
            .count();
    if (children > 0) {
      throw SystemErrors.of(
          SystemErrorCodes.DICT_ITEM_HAS_CHILDREN, item.getCode() + "(" + children + " 个子项)");
    }
    detachItemRelations(item.getTypeCode(), item.getCode(), tenantId);
    dictItemRepository.deleteById(id);
    dictOptionsCache.evictType(item.getTypeCode());
  }

  /**
   * 移动节点：作用于<b>指定层级视图</b>，校验环与深度。
   *
   * <p>环检测基于 {@code path}——若目标父节点的路径里含自身编码 {@code /SELF/}， 说明正把自己挂到自己的子孙下，直接拒绝（不必再上溯，path 已物化整条链路）。
   */
  @Transactional
  public void moveItem(MoveDictItemCommand command) {
    SysDictItem item = requireItem(command.getId());
    long tenantId = currentTenant();
    SysDictType type = requireTypeByCode(item.getTypeCode());
    assertItemsEditable(type, tenantId);
    String view = SysDictHierarchy.normalizeHierarchyCode(command.getHierarchyCode());
    SysDictHierarchy node =
        dictHierarchyRepository
            .findByCodeAllTenants(type.getCode(), view, item.getCode(), tenantId)
            .orElseThrow(
                SystemErrors.supplier(
                    SystemErrorCodes.DICT_HIERARCHY_NOT_FOUND, type.getCode() + "/" + view));
    String parentCode = command.getParentCode();
    if (parentCode == null || parentCode.isBlank()) {
      node.moveTo(null, null, null, command.getSort());
    } else {
      if (!type.category().isCascade()) {
        throw SystemErrors.of(
            SystemErrorCodes.DICT_PARENT_NOT_ALLOWED, type.getCode() + " 不是 CASCADE 类值域");
      }
      SysDictHierarchy parent =
          dictHierarchyRepository
              .findByCodeAllTenants(type.getCode(), view, parentCode, tenantId)
              .orElseThrow(
                  SystemErrors.supplier(
                      SystemErrorCodes.DICT_PARENT_NOT_FOUND, type.getCode() + "/" + parentCode));
      if (parent.hasAncestor(item.getCode())) {
        throw SystemErrors.of(SystemErrorCodes.DICT_CYCLE_DETECTED, item.getCode());
      }
      if (type.getMaxDepth() != null
          && type.getMaxDepth() > 0
          && parent.getLevel() + 1 > type.getMaxDepth()) {
        throw SystemErrors.of(
            SystemErrorCodes.DICT_CASCADE_DEPTH_EXCEEDED,
            (parent.getLevel() + 1) + " > " + type.getMaxDepth());
      }
      node.moveTo(parentCode, parent.getLevel(), parent.getPath(), command.getSort());
    }
    dictHierarchyRepository.save(node);
    dictOptionsCache.evictType(item.getTypeCode());
  }

  /** 设为该类型的默认项（同租户作用域内唯一）。 */
  @Transactional
  public void markDefault(Long id) {
    SysDictItem item = requireItem(id);
    long tenantId = currentTenant();
    clearDefaultInScope(item.getTypeCode(), tenantId);
    item.markAsDefault();
    dictItemRepository.save(item);
    dictOptionsCache.evictType(item.getTypeCode());
  }

  /** 按类型 + 编码取项（租户覆盖优先）。 */
  public Optional<DictItemDto> getItem(String typeCode, String code) {
    return dictItemRepository
        .findByTypeAndCodeAllTenants(typeCode, code, currentTenant())
        .map(DictItemDto::from);
  }

  /** 项分页（扁平视图）：合并平台与租户项后按关键字 / 状态过滤，可选按父级收窄。 */
  public PageResult<DictItemDto> pageItems(DictItemPageQuery query) {
    if (query.getTypeCode() == null || query.getTypeCode().isBlank()) {
      return PageResult.of(List.of(), 0L, query.getPageNum(), query.getPageSize());
    }
    long tenantId = currentTenant();
    List<SysDictItem> merged = mergedItems(query.getTypeCode(), tenantId);
    if (query.getParentCode() != null && !query.getParentCode().isBlank()) {
      List<String> children =
          dictHierarchyRepository
              .listByTypeAllTenants(
                  query.getTypeCode(),
                  SysDictHierarchy.normalizeHierarchyCode(query.getHierarchyCode()),
                  tenantId)
              .stream()
              .filter(n -> query.getParentCode().equals(n.getParentCode()))
              .map(SysDictHierarchy::getCode)
              .toList();
      merged = merged.stream().filter(i -> children.contains(i.getCode())).toList();
    }
    String keyword = query.getKeyword();
    List<DictItemDto> filtered =
        merged.stream()
            .filter(i -> query.getStatus() == null || query.getStatus().equals(i.getStatus()))
            .filter(i -> matchesKeyword(keyword, i.getCode(), i.getLabel()))
            .sorted(ITEM_ORDER)
            .map(DictItemDto::from)
            .toList();
    return paginate(filtered, query.getPageNum(), query.getPageSize());
  }

  /** 指定层级视图的树。关系指向已删项、或项尚未挂任何层级时，一律落到根—— 宁可让节点挂在根上被看见，也不要因为一条脏关系让整棵树渲染不出来。 */
  public List<DictItemDto> treeItems(String typeCode, String hierarchyCode) {
    long tenantId = currentTenant();
    String view = SysDictHierarchy.normalizeHierarchyCode(hierarchyCode);
    Map<String, SysDictItem> items = new LinkedHashMap<>();
    mergedItems(typeCode, tenantId).forEach(i -> items.put(i.getCode(), i));

    Map<String, DictItemDto> byCode = new LinkedHashMap<>();
    for (SysDictHierarchy node :
        dictHierarchyRepository.listByTypeAllTenants(typeCode, view, tenantId)) {
      SysDictItem item = items.get(node.getCode());
      if (item == null) {
        continue;
      }
      DictItemDto dto = DictItemDto.from(item);
      dto.setHierarchyCode(node.getHierarchyCode());
      dto.setParentCode(node.getParentCode());
      dto.setLevel(node.getLevel());
      dto.setPath(node.getPath());
      byCode.put(node.getCode(), dto);
    }
    items
        .values()
        .forEach(
            item ->
                byCode.computeIfAbsent(
                    item.getCode(),
                    ignored -> {
                      DictItemDto dto = DictItemDto.from(item);
                      dto.setHierarchyCode(view);
                      dto.setLevel(1);
                      return dto;
                    }));

    List<DictItemDto> roots = new ArrayList<>();
    for (DictItemDto node : byCode.values()) {
      DictItemDto parent = node.getParentCode() == null ? null : byCode.get(node.getParentCode());
      if (parent == null || parent == node) {
        roots.add(node);
      } else {
        parent.setHasChildren(true);
        parent.getChildren().add(node);
      }
    }
    return roots;
  }

  /**
   * 下拉数据源：全平台消费字典的唯一入口（平台+租户覆盖合并 → 生效过滤 → 只返启用项 → 可选本地化，走缓存）。
   *
   * @param parentCode 顶层传 {@code null}；非空时按层级视图取该父节点的直接子节点
   * @param language 为空表示不本地化，用默认 {@code label}
   */
  public List<DictOptionDto> options(String typeCode, String parentCode, String language) {
    long tenantId = currentTenant();
    List<SysDictItem> items =
        dictOptionsCache
            .get(tenantId, typeCode, parentCode, language)
            .orElseGet(
                () -> {
                  List<SysDictItem> loaded =
                      mergedItems(typeCode, tenantId).stream()
                          .filter(SysDictItem::isEnabled)
                          .filter(SysDictItem::isEffectiveNow)
                          .filter(i -> inHierarchy(typeCode, parentCode, i.getCode(), tenantId))
                          .sorted(ITEM_ORDER)
                          .toList();
                  dictOptionsCache.put(tenantId, typeCode, parentCode, language, loaded);
                  return loaded;
                });
    Map<String, String> texts =
        language == null || language.isBlank()
            ? Map.of()
            : localizedLabels(typeCode, language, tenantId);
    return items.stream()
        .map(
            item -> {
              DictOptionDto dto = DictOptionDto.from(item);
              String translated = texts.get(item.getCode());
              if (translated != null && !translated.isBlank()) {
                dto.setLabel(translated);
              }
              return dto;
            })
        .toList();
  }

  // ==================== 层级视图 ====================

  /** 该值域已有的层级视图编码（含 DEFAULT）。 */
  public List<String> listHierarchies(String typeCode) {
    return dictHierarchyRepository.listAllViewsAllTenants(typeCode, currentTenant()).stream()
        .map(SysDictHierarchy::getHierarchyCode)
        .distinct()
        .sorted()
        .toList();
  }

  // ==================== 多语言译文 ====================

  public List<DictItemTextDto> listTexts(String typeCode, String code) {
    return dictItemTextRepository.listByItemAllTenants(typeCode, code, currentTenant()).stream()
        .map(DictItemTextDto::from)
        .toList();
  }

  /** 批量保存某项的译文（按 language upsert）。 */
  @Transactional
  public int saveTexts(String typeCode, String code, List<DictItemTextDto> payload) {
    if (payload == null || payload.isEmpty()) {
      return 0;
    }
    long tenantId = currentTenant();
    DictCode type = parseTypeCode(typeCode);
    DictCode itemCode = parseItemCode(code);
    int affected = 0;
    for (DictItemTextDto dto : payload) {
      Optional<SysDictItemText> hit =
          dictItemTextRepository.findByItemAndLanguageAllTenants(
              type.value(), itemCode.value(), dto.getLanguage(), tenantId);
      if (hit.isPresent() && Objects.equals(hit.get().getTenantId(), tenantId)) {
        SysDictItemText text = hit.get();
        text.update(dto.getLabel(), dto.getDescription());
        dictItemTextRepository.save(text);
      } else {
        dictItemTextRepository.save(
            SysDictItemText.create(
                DistributedIdGenerator.generateLongId(),
                tenantId,
                type,
                itemCode,
                dto.getLanguage(),
                dto.getLabel(),
                dto.getDescription()));
      }
      affected++;
    }
    dictOptionsCache.evictType(type.value());
    return affected;
  }

  // ==================== 枚举绑定 ====================

  /** 枚举与字典的漂移检查（不写库）。 */
  public DictEnumDiffDto enumDiff(String typeCode) {
    SysDictType type = requireTypeByCode(typeCode);
    List<DictEnumScanner.DictEnumConstant> constants = scanEnum(type);
    Map<String, SysDictItem> itemsByCode =
        mergedItems(type.getCode(), currentTenant()).stream()
            .collect(LinkedHashMap::new, (m, i) -> m.put(i.getCode(), i), Map::putAll);
    List<String> missingInDict = new ArrayList<>();
    List<String> valueDrift = new ArrayList<>();
    for (DictEnumScanner.DictEnumConstant c : constants) {
      SysDictItem item = itemsByCode.get(c.name());
      if (item == null) {
        missingInDict.add(c.name());
      } else if (!String.valueOf(c.ordinal()).equals(item.getValue())) {
        valueDrift.add(c.name());
      }
    }
    List<String> enumNames =
        constants.stream().map(DictEnumScanner.DictEnumConstant::name).toList();
    List<String> missingInEnum =
        itemsByCode.keySet().stream().filter(code -> !enumNames.contains(code)).toList();
    return DictEnumDiffDto.builder()
        .typeCode(type.getCode())
        .enumClass(type.getEnumClass())
        .missingInDict(missingInDict)
        .missingInEnum(missingInEnum)
        .valueDrift(valueDrift)
        .build();
  }

  /**
   * 按枚举同步字典项（幂等）：只补缺失项、刷新 value/ordinal，<b>不覆盖人工润色过的 label</b>。
   *
   * @return 本次受影响（新建 + 更新）的条目数
   */
  @Transactional
  public int syncEnum(String typeCode) {
    SysDictType type = requireTypeByCode(typeCode);
    long tenantId = currentTenant();
    assertItemsEditable(type, tenantId);
    List<DictEnumScanner.DictEnumConstant> constants = scanEnum(type);
    Map<String, SysDictItem> existing =
        mergedItems(type.getCode(), tenantId).stream()
            .collect(LinkedHashMap::new, (m, i) -> m.put(i.getCode(), i), Map::putAll);
    int affected = 0;
    for (DictEnumScanner.DictEnumConstant c : constants) {
      SysDictItem item = existing.get(c.name());
      if (item == null) {
        dictItemRepository.save(
            SysDictItem.create(
                DistributedIdGenerator.generateLongId(),
                tenantId,
                DictCode.typeCode(type.getCode()),
                DictCode.of(c.name()),
                c.name(),
                String.valueOf(c.ordinal()),
                c.name(),
                DictTagType.DEFAULT,
                null,
                null,
                null,
                null,
                false,
                c.ordinal(),
                1,
                null));
        affected++;
      } else if (!String.valueOf(c.ordinal()).equals(item.getValue())) {
        item.update(
            null, String.valueOf(c.ordinal()), null, null, null, null, null, null, null, null);
        dictItemRepository.save(item);
        affected++;
      }
    }
    dictOptionsCache.evictType(type.getCode());
    return affected;
  }

  // ==================== 导入导出 ====================

  /** 导出值域快照（类型定义 + 项 + 层级关系 + 译文）。 */
  public Optional<DictExportDto> exportType(String typeCode) {
    long tenantId = currentTenant();
    return dictTypeRepository
        .findByCodeAllTenants(typeCode, tenantId)
        .map(
            type ->
                DictExportDto.builder()
                    .type(DictTypeDto.from(type))
                    .items(
                        mergedItems(type.getCode(), tenantId).stream()
                            .sorted(ITEM_ORDER)
                            .map(DictItemDto::from)
                            .toList())
                    .hierarchies(
                        dictHierarchyRepository
                            .listAllViewsAllTenants(type.getCode(), tenantId)
                            .stream()
                            .map(DictHierarchyDto::from)
                            .toList())
                    .texts(
                        mergedItems(type.getCode(), tenantId).stream()
                            .flatMap(
                                item ->
                                    dictItemTextRepository
                                        .listByItemAllTenants(
                                            type.getCode(), item.getCode(), tenantId)
                                        .stream()
                                        .map(DictItemTextDto::from))
                            .toList())
                    .build());
  }

  /** 导入值域（upsert）：项按 code 覆盖，随后按快照重建层级关系与译文。 */
  @Transactional
  public int importType(String typeCode, DictExportDto payload) {
    SysDictType type = requireTypeByCode(typeCode);
    if (payload == null || payload.getItems() == null || payload.getItems().isEmpty()) {
      return 0;
    }
    if (payload.getItems().size() > MAX_IMPORT_ITEMS) {
      throw SystemErrors.of(
          SystemErrorCodes.DICT_IMPORT_TOO_LARGE,
          payload.getItems().size() + " > " + MAX_IMPORT_ITEMS);
    }
    long tenantId = currentTenant();
    assertItemsEditable(type, tenantId);
    // 只导入项与关系；类型定义不随快照覆盖（一个事务只改一个聚合 R9，且真源配置跨环境差异应人工确认）
    int affected = 0;
    for (DictItemDto dto : payload.getItems()) {
      DictCode code = parseItemCode(dto.getCode());
      assertValueFormat(type, dto.getValue());
      Optional<SysDictItem> hit =
          dictItemRepository.findByTypeAndCodeAllTenants(type.getCode(), code.value(), tenantId);
      if (hit.isPresent() && Objects.equals(hit.get().getTenantId(), tenantId)) {
        SysDictItem item = hit.get();
        item.update(
            dto.getLabel(),
            dto.getValue(),
            DictTagType.ofCode(dto.getTagType()),
            dto.getI18nKey(),
            dto.getExternalCode(),
            dto.getEffectiveFrom(),
            dto.getEffectiveTo(),
            dto.getSort(),
            dto.getStatus(),
            dto.getDescription());
        dictItemRepository.save(item);
      } else {
        dictItemRepository.save(
            SysDictItem.create(
                DistributedIdGenerator.generateLongId(),
                tenantId,
                DictCode.typeCode(type.getCode()),
                code,
                dto.getLabel() == null ? code.value() : dto.getLabel(),
                dto.getValue(),
                dto.getEnumName(),
                DictTagType.ofCode(dto.getTagType()),
                dto.getI18nKey(),
                dto.getExternalCode(),
                dto.getEffectiveFrom(),
                dto.getEffectiveTo(),
                dto.isDefaultItem(),
                dto.getSort(),
                dto.getStatus(),
                dto.getDescription()));
      }
      affected++;
    }
    importHierarchies(type, payload.getHierarchies(), tenantId);
    importTexts(type.getCode(), payload.getTexts(), tenantId);
    dictOptionsCache.evictType(type.getCode());
    return affected;
  }

  // ==================== 内部：层级关系 ====================

  /** 新建项后挂载层级（private：跨聚合写不落在用例入口的直接调用里，R9）。 */
  private void attachHierarchy(
      SysDictType type,
      SysDictItem item,
      String hierarchyCode,
      String parentCode,
      Integer sort,
      long tenantId) {
    if (!type.category().isCascade() && (parentCode == null || parentCode.isBlank())) {
      return;
    }
    String view = SysDictHierarchy.normalizeHierarchyCode(hierarchyCode);
    Map<String, SysDictHierarchy> nodes = hierarchyMap(type.getCode(), view, tenantId);
    String resolved = resolveParentCode(type, item.getCode(), parentCode, nodes);
    SysDictHierarchy parent = resolved == null ? null : nodes.get(resolved);
    if (parent != null && parent.getLevel() + 1 > maxDepthOf(type)) {
      throw SystemErrors.of(
          SystemErrorCodes.DICT_CASCADE_DEPTH_EXCEEDED,
          (parent.getLevel() + 1) + " > " + type.getMaxDepth());
    }
    dictHierarchyRepository.save(
        SysDictHierarchy.create(
            DistributedIdGenerator.generateLongId(),
            tenantId,
            DictCode.typeCode(type.getCode()),
            view,
            DictCode.of(item.getCode()),
            resolved,
            parent == null ? null : parent.getLevel(),
            parent == null ? null : parent.getPath(),
            sort == null ? item.getSort() : sort));
  }

  /** 决定父级编码：显式指定优先；未指定且类型配了 {@code codeSegments} 时按编码前缀推导 （GB/T 2260 风格）——推导出的父编码不存在则退化为根，不阻断导入。 */
  private String resolveParentCode(
      SysDictType type, String itemCode, String parentCode, Map<String, SysDictHierarchy> nodes) {
    if (parentCode != null && !parentCode.isBlank()) {
      if (!type.category().isCascade()) {
        throw SystemErrors.of(
            SystemErrorCodes.DICT_PARENT_NOT_ALLOWED, type.getCode() + " 不是 CASCADE 类值域");
      }
      if (!nodes.containsKey(parentCode)) {
        throw SystemErrors.of(
            SystemErrorCodes.DICT_PARENT_NOT_FOUND, type.getCode() + "/" + parentCode);
      }
      return parentCode;
    }
    return type.deriveParentCode(itemCode).filter(nodes::containsKey).orElse(null);
  }

  /** 删项时清理其在全部层级视图中的关系与译文（private，同 attachHierarchy 的理由）。 */
  private void detachItemRelations(String typeCode, String code, long tenantId) {
    dictHierarchyRepository.removeByCodeAllTenants(typeCode, code, tenantId);
    dictItemTextRepository.removeByItemAllTenants(typeCode, code, tenantId);
  }

  /** 导入层级关系：按 (hierarchyCode, code) upsert，父级不存在则挂根（不阻断）。 */
  private void importHierarchies(SysDictType type, List<DictHierarchyDto> payload, long tenantId) {
    if (payload == null || payload.isEmpty()) {
      return;
    }
    Map<String, Map<String, SysDictHierarchy>> byView = new LinkedHashMap<>();
    for (DictHierarchyDto dto : payload) {
      String view = SysDictHierarchy.normalizeHierarchyCode(dto.getHierarchyCode());
      Map<String, SysDictHierarchy> nodes =
          byView.computeIfAbsent(view, v -> hierarchyMap(type.getCode(), v, tenantId));
      SysDictHierarchy parent = dto.getParentCode() == null ? null : nodes.get(dto.getParentCode());
      Optional<SysDictHierarchy> existing = Optional.ofNullable(nodes.get(dto.getCode()));
      if (existing.isPresent() && Objects.equals(existing.get().getTenantId(), tenantId)) {
        existing
            .get()
            .moveTo(
                parent == null ? null : parent.getCode(),
                parent == null ? null : parent.getLevel(),
                parent == null ? null : parent.getPath(),
                dto.getSort());
        dictHierarchyRepository.save(existing.get());
      } else {
        SysDictHierarchy created =
            SysDictHierarchy.create(
                DistributedIdGenerator.generateLongId(),
                tenantId,
                DictCode.typeCode(type.getCode()),
                view,
                parseItemCode(dto.getCode()),
                parent == null ? null : parent.getCode(),
                parent == null ? null : parent.getLevel(),
                parent == null ? null : parent.getPath(),
                dto.getSort());
        dictHierarchyRepository.save(created);
        nodes.put(dto.getCode(), created);
      }
    }
  }

  private void importTexts(String typeCode, List<DictItemTextDto> payload, long tenantId) {
    if (payload == null || payload.isEmpty()) {
      return;
    }
    for (DictItemTextDto dto : payload) {
      if (dto.getLanguage() == null || dto.getLanguage().isBlank()) {
        continue;
      }
      Optional<SysDictItemText> hit =
          dictItemTextRepository.findByItemAndLanguageAllTenants(
              typeCode, dto.getCode(), dto.getLanguage(), tenantId);
      if (hit.isPresent() && Objects.equals(hit.get().getTenantId(), tenantId)) {
        hit.get().update(dto.getLabel(), dto.getDescription());
        dictItemTextRepository.save(hit.get());
      } else {
        dictItemTextRepository.save(
            SysDictItemText.create(
                DistributedIdGenerator.generateLongId(),
                tenantId,
                parseTypeCode(typeCode),
                parseItemCode(dto.getCode()),
                dto.getLanguage(),
                dto.getLabel(),
                dto.getDescription()));
      }
    }
  }

  private Map<String, SysDictHierarchy> hierarchyMap(
      String typeCode, String hierarchyCode, long tenantId) {
    Map<String, SysDictHierarchy> nodes = new LinkedHashMap<>();
    dictHierarchyRepository
        .listByTypeAllTenants(typeCode, hierarchyCode, tenantId)
        .forEach(n -> nodes.put(n.getCode(), n));
    return nodes;
  }

  /** 下拉按父级收窄：未指定父级时返回全部（列表/枚举值域的常见用法）。 */
  private boolean inHierarchy(String typeCode, String parentCode, String code, long tenantId) {
    if (parentCode == null || parentCode.isBlank()) {
      return true;
    }
    return dictHierarchyRepository
        .findByCodeAllTenants(typeCode, SysDictHierarchy.DEFAULT_HIERARCHY, code, tenantId)
        .filter(n -> parentCode.equals(n.getParentCode()))
        .isPresent();
  }

  private Map<String, String> localizedLabels(String typeCode, String language, long tenantId) {
    Map<String, String> labels = new LinkedHashMap<>();
    dictItemTextRepository
        .listByTypeAndLanguageAllTenants(typeCode, language, tenantId)
        .forEach(t -> labels.put(t.getCode(), t.getLabel()));
    return labels;
  }

  private int maxDepthOf(SysDictType type) {
    return type.getMaxDepth() == null || type.getMaxDepth() <= 0
        ? Integer.MAX_VALUE
        : type.getMaxDepth();
  }

  // ==================== 内部：覆盖模型与校验 ====================

  /** 平台类型 + 租户类型，按 code 去重（租户行胜出）。 */
  private List<SysDictType> mergedTypes() {
    List<SysDictType> rows = dictTypeRepository.listAllTenants(currentTenant());
    Map<String, SysDictType> byCode = new LinkedHashMap<>();
    for (SysDictType t : rows) {
      SysDictType cur = byCode.get(t.getCode());
      if (cur == null || !isPlatformRow(t)) {
        byCode.put(t.getCode(), t);
      }
    }
    return new ArrayList<>(byCode.values());
  }

  /** 平台项 + 租户项，按 code 去重（租户行胜出）。 */
  private List<SysDictItem> mergedItems(String typeCode, long tenantId) {
    List<SysDictItem> rows = dictItemRepository.listByTypeAllTenants(typeCode, tenantId);
    Map<String, SysDictItem> byCode = new LinkedHashMap<>();
    for (SysDictItem i : rows) {
      SysDictItem cur = byCode.get(i.getCode());
      if (cur == null || !isPlatformRow(i)) {
        byCode.put(i.getCode(), i);
      }
    }
    return new ArrayList<>(byCode.values());
  }

  private static boolean isPlatformRow(SysDictItem item) {
    return item.getTenantId() == null || item.getTenantId() == 0L;
  }

  private static boolean isPlatformRow(SysDictType type) {
    return type.getTenantId() == null || type.getTenantId() == 0L;
  }

  /** 同租户作用域内清除既有默认项（租户只能管自己的默认，平台默认由平台侧维护）。 */
  private void clearDefaultInScope(String typeCode, long tenantId) {
    for (SysDictItem item : mergedItems(typeCode, tenantId)) {
      if (item.isDefaultItem() && Objects.equals(item.getTenantId(), tenantId)) {
        item.clearDefault();
        dictItemRepository.save(item);
      }
    }
  }

  private void assertItemsEditable(SysDictType type, long tenantId) {
    if (tenantId != 0L) {
      try {
        type.assertEditableByTenant();
      } catch (RuntimeException ex) {
        throw SystemErrors.of(SystemErrorCodes.DICT_TYPE_READONLY, ex.getMessage());
      }
    }
  }

  private void assertValueFormat(SysDictType type, String value) {
    try {
      type.assertValueFormat(value);
    } catch (RuntimeException ex) {
      throw SystemErrors.of(SystemErrorCodes.DICT_VALUE_INVALID, ex.getMessage());
    }
  }

  private void applyValueFormat(
      SysDictType type, String valueType, String valueRegex, String codeSegments) {
    if (valueType == null && valueRegex == null && codeSegments == null) {
      return;
    }
    try {
      type.applyValueFormat(valueType, valueRegex, codeSegments);
    } catch (RuntimeException ex) {
      throw SystemErrors.of(SystemErrorCodes.DICT_VALUE_INVALID, ex.getMessage());
    }
  }

  private List<DictEnumScanner.DictEnumConstant> scanEnum(SysDictType type) {
    if (type.getEnumClass() == null || type.getEnumClass().isBlank()) {
      throw SystemErrors.of(SystemErrorCodes.DICT_ENUM_CLASS_INVALID, type.getCode() + " 未绑定枚举类");
    }
    try {
      return dictEnumScanner.scan(type.getEnumClass());
    } catch (RuntimeException ex) {
      throw SystemErrors.of(
          SystemErrorCodes.DICT_ENUM_CLASS_INVALID, type.getEnumClass() + " → " + ex.getMessage());
    }
  }

  private SysDictType requireTypeById(Long id) {
    SysDictType type = dictTypeRepository.findById(id);
    if (type == null) {
      throw SystemErrors.of(SystemErrorCodes.DICT_TYPE_NOT_FOUND, id);
    }
    return type;
  }

  private SysDictType requireTypeByCode(String typeCode) {
    return dictTypeRepository
        .findByCodeAllTenants(typeCode, currentTenant())
        .orElseThrow(SystemErrors.supplier(SystemErrorCodes.DICT_TYPE_NOT_FOUND, typeCode));
  }

  private SysDictItem requireItem(Long id) {
    SysDictItem item = dictItemRepository.findById(id);
    if (item == null) {
      throw SystemErrors.of(SystemErrorCodes.DICT_ITEM_NOT_FOUND, id);
    }
    return item;
  }

  private long currentTenant() {
    Long tenantId = tenantProvider.currentTenantIdOrNull();
    return tenantId == null ? 0L : tenantId;
  }

  private static SysDictItem newItem(
      SysDictType type, long tenantId, DictCode code, CreateDictItemCommand command) {
    assertRange(command.getEffectiveFrom(), command.getEffectiveTo());
    return SysDictItem.create(
        DistributedIdGenerator.generateLongId(),
        tenantId,
        DictCode.typeCode(type.getCode()),
        code,
        command.getLabel(),
        command.getValue(),
        command.getEnumName(),
        DictTagType.ofCode(command.getTagType()),
        command.getI18nKey(),
        command.getExternalCode(),
        command.getEffectiveFrom(),
        command.getEffectiveTo(),
        Boolean.TRUE.equals(command.getIsDefault()),
        command.getSort(),
        command.getStatus(),
        command.getDescription());
  }

  private static void assertRange(LocalDateTime from, LocalDateTime to) {
    if (from != null && to != null && from.isAfter(to)) {
      throw SystemErrors.of(SystemErrorCodes.DICT_EFFECTIVE_RANGE_INVALID, from + " > " + to);
    }
  }

  private static DictCode parseTypeCode(String value) {
    try {
      return DictCode.typeCode(value);
    } catch (RuntimeException ex) {
      throw SystemErrors.of(SystemErrorCodes.DICT_CODE_INVALID, value);
    }
  }

  private static DictCode parseItemCode(String value) {
    try {
      return DictCode.of(value);
    } catch (RuntimeException ex) {
      throw SystemErrors.of(SystemErrorCodes.DICT_CODE_INVALID, value);
    }
  }

  private static DictCategory parseCategory(String value) {
    try {
      return DictCategory.of(value);
    } catch (RuntimeException ex) {
      throw SystemErrors.of(SystemErrorCodes.DICT_CATEGORY_INVALID, value);
    }
  }

  private static boolean matchesKeyword(String keyword, String... candidates) {
    if (keyword == null || keyword.isBlank()) {
      return true;
    }
    String kw = keyword.trim().toLowerCase();
    for (String c : candidates) {
      if (c != null && c.toLowerCase().contains(kw)) {
        return true;
      }
    }
    return false;
  }

  private static <T> PageResult<T> paginate(List<T> all, int pageNum, int pageSize) {
    int pn = Math.max(1, pageNum);
    int ps = Math.max(1, pageSize);
    int from = Math.min((pn - 1) * ps, all.size());
    int to = Math.min(from + ps, all.size());
    return PageResult.of(new ArrayList<>(all.subList(from, to)), (long) all.size(), pn, ps);
  }
}
