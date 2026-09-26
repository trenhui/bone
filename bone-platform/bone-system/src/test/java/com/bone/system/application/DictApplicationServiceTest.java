package com.bone.system.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bone.core.domain.event.DomainEventPublisher;
import com.bone.core.exception.BizException;
import com.bone.system.application.command.CreateDictItemCommand;
import com.bone.system.application.command.MoveDictItemCommand;
import com.bone.system.application.query.dto.DictExportDto;
import com.bone.system.application.query.dto.DictItemDto;
import com.bone.system.application.query.dto.DictOptionDto;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 字典 v3 的应用层链路测试。
 *
 * <p>覆盖的是<b>编排与口径</b>，不是聚合内部不变量（后者在 {@code SysDict*Test} 纯单测里）：
 *
 * <ul>
 *   <li>层级是否真的落到关系表（而不是项的字段）——v3 与 v2 的分水岭；
 *   <li>环检测 / 深度上限 / 有子禁删 是否翻译成正确错误码；
 *   <li>{@code /options} 的生效过滤与语言本地化是否生效；
 *   <li>编码分段能否在导入/新建时推导出父级（GB/T 2260 口径）。
 * </ul>
 *
 * <p>用 Mockito 而非 {@code @SpringBootTest}：SDK 仓储在本模块测试上下文未真正装配， 起容器既慢又会把「编排逻辑」和「基础设施可用性」两个变量耦在一起。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DictApplicationServiceTest {

  @Mock SysDictTypeRepository dictTypeRepository;
  @Mock SysDictItemRepository dictItemRepository;
  @Mock SysDictHierarchyRepository dictHierarchyRepository;
  @Mock SysDictItemTextRepository dictItemTextRepository;
  @Mock DictEnumScanner dictEnumScanner;
  @Mock DictOptionsCache dictOptionsCache;
  @Mock TenantProvider tenantProvider;
  @Mock DomainEventPublisher domainEventPublisher;

  DictApplicationService service;

  @BeforeEach
  void setUp() {
    service =
        new DictApplicationService(
            dictTypeRepository,
            dictItemRepository,
            dictHierarchyRepository,
            dictItemTextRepository,
            dictEnumScanner,
            dictOptionsCache,
            tenantProvider,
            domainEventPublisher);
    when(tenantProvider.currentTenantIdOrNull()).thenReturn(0L);
    when(dictOptionsCache.get(anyLong(), anyString(), any(), any())).thenReturn(Optional.empty());
  }

  // ---------- 层级挂载 ----------

  @Test
  void createItemAttachesHierarchyInsteadOfItemField() {
    SysDictType type = cascadeType(3, null);
    stubType(type);
    stubHierarchy(List.of(hierarchyNode("GD", null, 1, "/GD/")));

    service.createItem(createItemCmd("biz_region", "GZ", "广州市", "GD"));

    ArgumentCaptor<SysDictHierarchy> captor = ArgumentCaptor.forClass(SysDictHierarchy.class);
    verify(dictHierarchyRepository).save(captor.capture());
    SysDictHierarchy saved = captor.getValue();
    assertThat(saved.getCode()).isEqualTo("GZ");
    assertThat(saved.getParentCode()).isEqualTo("GD");
    assertThat(saved.getPath()).isEqualTo("/GD/GZ/");
    assertThat(saved.getLevel()).isEqualTo(2);
    assertThat(saved.getHierarchyCode()).isEqualTo(SysDictHierarchy.DEFAULT_HIERARCHY);
  }

  @Test
  void createItemDerivesParentFromCodeSegments() {
    SysDictType type = cascadeType(3, "2,2,2");
    stubType(type);
    // 平台已存在 440000（广东省）；新建 440100（广州市）不指定父级
    stubHierarchy(List.of(hierarchyNode("440000", null, 1, "/440000/")));

    service.createItem(createItemCmd("biz_region", "440100", "广州市", null));

    ArgumentCaptor<SysDictHierarchy> captor = ArgumentCaptor.forClass(SysDictHierarchy.class);
    verify(dictHierarchyRepository).save(captor.capture());
    assertThat(captor.getValue().getParentCode()).isEqualTo("440000");
    assertThat(captor.getValue().getPath()).isEqualTo("/440000/440100/");
  }

  @Test
  void moveIntoOwnDescendantIsRejectedAsCycle() {
    SysDictType type = cascadeType(5, null);
    stubType(type);
    SysDictItem gz = item("GZ");
    when(dictItemRepository.findById(99L)).thenReturn(gz);
    stubHierarchy(
        List.of(
            hierarchyNode("GD", null, 1, "/GD/"),
            hierarchyNode("GZ", "GD", 2, "/GD/GZ/"),
            hierarchyNode("NS", "GZ", 3, "/GD/GZ/NS/")));
    when(dictHierarchyRepository.findByCodeAllTenants("biz_region", "DEFAULT", "GZ", 0L))
        .thenReturn(Optional.of(hierarchyNode("GZ", "GD", 2, "/GD/GZ/")));
    when(dictHierarchyRepository.findByCodeAllTenants("biz_region", "DEFAULT", "NS", 0L))
        .thenReturn(Optional.of(hierarchyNode("NS", "GZ", 3, "/GD/GZ/NS/")));

    assertThatThrownBy(() -> service.moveItem(moveCmd(99L, "NS")))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("SYS_DICT_CYCLE_DETECTED");
  }

  @Test
  void depthBeyondMaxDepthIsRejected() {
    SysDictType type = cascadeType(2, null);
    stubType(type);
    // 移动一个尚未挂入层级树的项 NEW 到 NS（已第 3 层）之下：会变成第 4 层，超过 maxDepth=2
    // （若移动的是树中已有项，多半会先命中环检测——那不是本用例要验的）
    when(dictItemRepository.findById(7L)).thenReturn(item("NEW"));
    stubHierarchy(
        List.of(
            hierarchyNode("GD", null, 1, "/GD/"),
            hierarchyNode("GZ", "GD", 2, "/GD/GZ/"),
            hierarchyNode("NS", "GZ", 3, "/GD/GZ/NS/"),
            hierarchyNode("NEW", null, 1, "/NEW/")));
    when(dictHierarchyRepository.findByCodeAllTenants("biz_region", "DEFAULT", "NS", 0L))
        .thenReturn(Optional.of(hierarchyNode("NS", "GZ", 3, "/GD/GZ/NS/")));
    when(dictHierarchyRepository.findByCodeAllTenants("biz_region", "DEFAULT", "NEW", 0L))
        .thenReturn(Optional.of(hierarchyNode("NEW", null, 1, "/NEW/")));

    assertThatThrownBy(() -> service.moveItem(moveCmd(7L, "NS")))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("SYS_DICT_CASCADE_DEPTH_EXCEEDED");
  }

  @Test
  void deleteItemRejectedWhenAnyHierarchyHasChildren() {
    SysDictType type = cascadeType(3, null);
    stubType(type);
    SysDictItem gd = item("GD");
    when(dictItemRepository.findById(5L)).thenReturn(gd);
    when(dictHierarchyRepository.listAllViewsAllTenants("biz_region", 0L))
        .thenReturn(
            List.of(hierarchyNode("GD", null, 1, "/GD/"), hierarchyNode("GZ", "GD", 2, "/GD/GZ/")));
    stubHierarchy(
        List.of(hierarchyNode("GD", null, 1, "/GD/"), hierarchyNode("GZ", "GD", 2, "/GD/GZ/")));

    assertThatThrownBy(() -> service.deleteItem(5L))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("SYS_DICT_ITEM_HAS_CHILDREN");
    verify(dictItemRepository, never()).deleteById(anyLong());
  }

  // ---------- 下拉数据源 ----------

  @Test
  void optionsOnlyReturnsEnabledAndEffectiveItems() {
    SysDictType type = listType();
    stubType(type);
    stubItems(
        List.of(
            plainItem("A", "有效", 1),
            withEffective(plainItem("B", "未生效", 1), LocalDateTime.now().plusDays(1), null),
            withEffective(
                plainItem("C", "已过期", 1),
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now().minusDays(1)),
            plainItem("D", "已停用", 0)));

    List<DictOptionDto> options = service.options("sys_status", null, null);

    assertThat(options).extracting(DictOptionDto::getCode).containsExactly("A");
  }

  @Test
  void optionsUsesServerSideTranslationWhenLanguageGiven() {
    SysDictType type = listType();
    stubType(type);
    stubItems(List.of(plainItem("ENABLED", "启用", 1)));
    when(dictItemTextRepository.listByTypeAndLanguageAllTenants("sys_status", "en-US", 0L))
        .thenReturn(List.of(text("ENABLED", "en-US", "Enabled")));

    List<DictOptionDto> options = service.options("sys_status", null, "en-US");

    assertThat(options).hasSize(1);
    assertThat(options.get(0).getLabel()).isEqualTo("Enabled");
  }

  @Test
  void optionsFallsBackToDefaultLabelWithoutTranslation() {
    SysDictType type = listType();
    stubType(type);
    stubItems(List.of(plainItem("ENABLED", "启用", 1)));
    when(dictItemTextRepository.listByTypeAndLanguageAllTenants("sys_status", "fr-FR", 0L))
        .thenReturn(List.of());

    List<DictOptionDto> options = service.options("sys_status", null, "fr-FR");

    assertThat(options.get(0).getLabel()).isEqualTo("启用");
  }

  // ---------- 值格式校验 ----------

  @Test
  void valueViolatingDomainIsRejected() {
    SysDictType type = listType();
    type.applyValueFormat("INT", null, null);
    stubType(type);
    when(dictItemRepository.findByTypeAndCodeAllTenants("sys_status", "X", 0L))
        .thenReturn(Optional.empty());
    CreateDictItemCommand cmd = createItemCmd("sys_status", "X", "标签", null);
    cmd.setValue("not-a-number");

    assertThatThrownBy(() -> service.createItem(cmd))
        .isInstanceOf(BizException.class)
        .hasMessageContaining("SYS_DICT_VALUE_INVALID");
  }

  // ---------- 导入导出 ----------

  @Test
  void importRebuildsHierarchyAndTexts() {
    SysDictType type = cascadeType(3, null);
    stubType(type);
    stubHierarchy(List.of());
    when(dictItemRepository.findByTypeAndCodeAllTenants(anyString(), anyString(), anyLong()))
        .thenReturn(Optional.empty());

    int affected =
        service.importType(
            "biz_region",
            DictExportDto.builder()
                .items(
                    List.of(
                        DictItemDto.builder().code("GD").label("广东省").sort(1).status(1).build(),
                        DictItemDto.builder()
                            .code("GZ")
                            .label("广州市")
                            .parentCode("GD")
                            .sort(1)
                            .status(1)
                            .build()))
                .hierarchies(
                    List.of(
                        com.bone.system.application.query.dto.DictHierarchyDto.builder()
                            .typeCode("biz_region")
                            .hierarchyCode("DEFAULT")
                            .code("GD")
                            .build(),
                        com.bone.system.application.query.dto.DictHierarchyDto.builder()
                            .typeCode("biz_region")
                            .hierarchyCode("DEFAULT")
                            .code("GZ")
                            .parentCode("GD")
                            .build()))
                .build());

    assertThat(affected).isEqualTo(2);
    verify(dictHierarchyRepository, org.mockito.Mockito.atLeast(2))
        .save(any(SysDictHierarchy.class));
  }

  // ---------- 辅助 ----------

  private void stubType(SysDictType type) {
    when(dictTypeRepository.findByCodeAllTenants(type.getCode(), 0L)).thenReturn(Optional.of(type));
  }

  private void stubItems(List<SysDictItem> items) {
    when(dictItemRepository.listByTypeAllTenants(anyString(), anyLong())).thenReturn(items);
  }

  private void stubHierarchy(List<SysDictHierarchy> nodes) {
    when(dictHierarchyRepository.listByTypeAllTenants(anyString(), anyString(), anyLong()))
        .thenReturn(nodes);
  }

  private static SysDictType listType() {
    return SysDictType.create(
        1L,
        0L,
        DictCode.typeCode("sys_status"),
        "系统状态",
        DictCategory.LIST,
        "system",
        null,
        null,
        null,
        false,
        0,
        1);
  }

  private static SysDictType cascadeType(int maxDepth, String codeSegments) {
    SysDictType type =
        SysDictType.create(
            2L,
            0L,
            DictCode.typeCode("biz_region"),
            "行政区域",
            DictCategory.CASCADE,
            "system",
            null,
            maxDepth,
            null,
            false,
            0,
            1);
    type.applyValueFormat(null, null, codeSegments);
    return type;
  }

  private static SysDictItem item(String code) {
    return plainItem(code, code, 1);
  }

  private static SysDictItem plainItem(String code, String label, int status) {
    return SysDictItem.create(
        10L,
        0L,
        DictCode.typeCode("biz_region"),
        DictCode.of(code),
        label,
        null,
        null,
        DictTagType.DEFAULT,
        null,
        null,
        null,
        null,
        false,
        0,
        status,
        null);
  }

  private static SysDictItem withEffective(SysDictItem base, LocalDateTime from, LocalDateTime to) {
    SysDictItem item =
        SysDictItem.create(
            base.getId(),
            0L,
            DictCode.typeCode(base.getTypeCode()),
            DictCode.of(base.getCode()),
            base.getLabel(),
            null,
            null,
            DictTagType.DEFAULT,
            null,
            null,
            from,
            to,
            false,
            0,
            base.getStatus(),
            null);
    return item;
  }

  private static SysDictHierarchy hierarchyNode(
      String code, String parentCode, int level, String path) {
    SysDictHierarchy node =
        SysDictHierarchy.create(
            100L,
            0L,
            DictCode.typeCode("biz_region"),
            "DEFAULT",
            DictCode.of(code),
            parentCode,
            level - 1,
            parentCode == null ? null : pathOfParent(path),
            0);
    return node;
  }

  /** 由子节点 path 反推父节点 path（/GD/GZ/ → /GD/）。 */
  private static String pathOfParent(String childPath) {
    int last = childPath.lastIndexOf('/', childPath.length() - 2);
    return last <= 0 ? "/" : childPath.substring(0, last + 1);
  }

  private static SysDictItemText text(String code, String language, String label) {
    return SysDictItemText.create(
        200L, 0L, DictCode.typeCode("sys_status"), DictCode.of(code), language, label, null);
  }

  private static CreateDictItemCommand createItemCmd(
      String typeCode, String code, String label, String parentCode) {
    CreateDictItemCommand cmd = new CreateDictItemCommand();
    cmd.setTypeCode(typeCode);
    cmd.setCode(code);
    cmd.setLabel(label);
    cmd.setParentCode(parentCode);
    cmd.setStatus(1);
    cmd.setSort(0);
    return cmd;
  }

  private static MoveDictItemCommand moveCmd(Long id, String parentCode) {
    MoveDictItemCommand cmd = new MoveDictItemCommand();
    cmd.setId(id);
    cmd.setParentCode(parentCode);
    return cmd;
  }
}
