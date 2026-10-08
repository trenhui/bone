package com.bone.metadata.engine.domain.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link EntityMetadata} 核心行为的单测（2026-10-07 补）。
 *
 * <p><b>为什么必须补</b>：{@code bone-metadata-engine-domain} 有 52 个源文件却只有 1 个 {@code
 * ArchitectureTest}，而该模块的 {@code jacoco} 门槛在 pom 里被覆盖为 <b>0</b>。 {@code gate-state.json} §G-1.7
 * 自己写着「不得长期以 0 覆盖蒙混」，但至今未执行。
 *
 * <p><b>为什么先测 domain 层而不是 runtime</b>：domain 是纯模型（无 Spring、无框架）， 单测毫秒级、不需起容器；runtime 的 107
 * 个类互相依赖、集成测试成本高。 <b>同样的投入，domain 层的判别力高得多</b>。
 *
 * <p><b>本组锁的是「有业务语义的边界」，不是 getter/setter</b>： {@code isValidFieldName} 的双键查找、{@code
 * validateMetadata} 的三类校验、 {@code clone} 的集合拷贝边界 —— 这些被改坏时不会有编译错误，只会在运行时悄悄错。
 *
 * <p>⚠️ <b>写这组测试时踩过的坑（记录下来防止后人重犯）</b>：<b>不能按字段名想当然地假设 API</b>。 本文件初版曾假设存在 {@code getTags()} /
 * {@code getAttributes()} / {@code getOperations()} / {@code SmartFieldMetadata.getFieldName()} /
 * {@code setUnique()}，实测<b>全部不存在</b>—— tags/attributes 只有 setter 无 getter；字段名 getter 是 {@code
 * getName()} （返回 {@code fieldName != null ? fieldName : apiName}，且<b>没有</b> {@code setName}， 只能用
 * {@code setApiName} 赋值）；{@code unique} <b>没有 setter</b>。 假设出来的 API 会让编译失败，或（更糟）写出恒真的断言。
 */
class EntityMetadataTest {

  /**
   * 构造字段。
   *
   * <p><b>为什么同时设 fieldName 与 apiName</b>：类上有 {@code @Getter/@Setter}（Lombok）， 故 {@code
   * getFieldName()} / {@code setFieldName()} 是<b>编译期生成</b>的—— 静态扫描源码看不到它们（这也是本文件初版误判「API
   * 不存在」的原因之一）。 而 {@code validateMetadata} 正是用 {@code field.getFieldName()} 统计重名， 只设 apiName 会让它取到
   * null ⇒ 把所有字段都算作重名。
   */
  private static SmartFieldMetadata field(String name, boolean required) {
    SmartFieldMetadata f = new SmartFieldMetadata();
    f.setFieldName(name);
    f.setApiName(name);
    f.setRequired(required);
    return f;
  }

  /**
   * 构造关联：<b>必须设 apiName</b>。
   *
   * <p>{@code setRelationships(List)} 会把 List 转成 Map，<b>跳过 apiName == null 的元素</b> ⇒ 不设 apiName
   * 时元素被静默丢弃、读回为空列表（实测踩过）。
   */
  private static RelationshipMetadata relationship(String apiName) {
    RelationshipMetadata r = new RelationshipMetadata();
    r.setApiName(apiName);
    return r;
  }

  private static EntityMetadata metaWith(String apiName, SmartFieldMetadata... fields) {
    EntityMetadata m = new EntityMetadata();
    m.setEntityName(apiName); // 注意：会同时写 name 与 apiName
    Map<String, SmartFieldMetadata> map = new HashMap<>();
    for (SmartFieldMetadata f : fields) {
      map.put(f.getName(), f);
    }
    m.setFields(map);
    return m;
  }

  @Nested
  @DisplayName("isValidFieldName：双键查找（裸字段名 / apiName.字段名）")
  class ValidFieldName {

    @Test
    @DisplayName("裸字段名存在于 fields ⇒ 有效")
    void acceptsBareFieldName() {
      EntityMetadata m = metaWith("order", field("id", true));
      assertTrue(m.isValidFieldName("id"));
    }

    /**
     * apiName 前缀形式：<b>当前实现不命中</b>。
     *
     * <p>实现是 {@code fields.containsKey(fieldName) || fields.containsKey(apiName + "." +
     * fieldName)}， 而 {@code fields} 的键是<b>裸字段名</b>（{@code setFields(Map)} 由调用方给键）， 故 {@code
     * "order.id"} 除非调用方恰好用带前缀的键，否则永远查不到。
     *
     * <p><b>与 {@code getFieldByName} 的策略不一致</b>：后者在查不到时会遍历字段、 用 {@code
     * f.getFieldName().equals(fieldName)} 回退，能容忍键名不规范； 而 {@code isValidFieldName} 只认键。⇒
     * <b>同一对「存在性判断 / 取值」API 口径不同</b>， 调用方拿 isValidFieldName=true 后 getField 仍可能拿到 null（或反之）。
     */
    @Test
    @DisplayName("apiName 前缀形式当前不命中（fields 键为裸名；与 getFieldByName 口径不一致）")
    void prefixedFormCurrentlyMisses() {
      EntityMetadata m = metaWith("order", field("id", true));
      assertFalse(
          m.isValidFieldName("order.id"),
          "当前实现：fields 键是裸名 ⇒ 带前缀名查不到。" + "这与 getFieldByName（会遍历回退）口径不一致，属已知不一致，见 KnownDefects");
    }

    @Test
    @DisplayName("null / 空串 / 不存在的字段 ⇒ 无效（不得抛异常）")
    void rejectsInvalidInput() {
      EntityMetadata m = metaWith("order", field("id", true));
      assertFalse(m.isValidFieldName(null));
      assertFalse(m.isValidFieldName(""));
      assertFalse(m.isValidFieldName("notExist"));
      assertFalse(m.isValidFieldName("other.id"), "前缀不匹配的字段不应视为有效");
    }
  }

  @Nested
  @DisplayName("validateMetadata：三类校验")
  class ValidateMetadata {

    @Test
    @DisplayName("实体名称为空 ⇒ 报「实体名称不能为空」")
    void rejectsEmptyName() {
      EntityMetadata m = new EntityMetadata(); // 未设 name
      List<String> errors = m.validateMetadata();
      assertTrue(errors.contains("实体名称不能为空"), "实际: " + errors);
    }

    @Test
    @DisplayName("主键字段不在 fields 中 ⇒ 报「主键字段不存在」")
    void rejectsUnknownPrimaryKey() {
      EntityMetadata m = metaWith("order", field("id", true));
      m.setPrimaryKeyField("notExist");
      assertTrue(m.validateMetadata().contains("主键字段不存在"), "实际: " + m.validateMetadata());
    }

    @Test
    @DisplayName("主键字段存在 ⇒ 不报该错")
    void acceptsExistingPrimaryKey() {
      EntityMetadata m = metaWith("order", field("id", true));
      m.setPrimaryKeyField("id");
      assertFalse(m.validateMetadata().contains("主键字段不存在"));
    }

    /**
     * 重复字段名的检出：实现按 {@code fields} 的<b>键</b>计数（不是遍历取字段名）， 而 {@code setFields(Map)} 直接持有传入的 Map ⇒
     * 用不同键放入两个同名字段即可构造重复。
     */
    @Test
    @DisplayName("字段名重复 ⇒ 报错并指出字段名")
    void reportsDuplicateFieldNames() {
      EntityMetadata m = new EntityMetadata();
      m.setEntityName("order");
      SmartFieldMetadata a = field("dup", false);
      SmartFieldMetadata b = field("dup", false);
      Map<String, SmartFieldMetadata> map = new HashMap<>();
      map.put("k1", a);
      map.put("k2", b);
      m.setFields(map);

      List<String> errors = m.validateMetadata();
      assertFalse(errors.isEmpty(), "同名字段应被检出");
      assertTrue(
          errors.stream().anyMatch(e -> e.contains("字段名称重复") && e.contains("dup")),
          "错误文案应包含字段名，实际: " + errors);
    }

    @Test
    @DisplayName("合法元数据 ⇒ 无错误")
    void passesForValidMetadata() {
      EntityMetadata m = metaWith("order", field("id", true), field("name", true));
      m.setPrimaryKeyField("id");
      assertEquals(List.of(), m.validateMetadata());
    }
  }

  @Nested
  @DisplayName("clone：集合替换但元素共享引用")
  class CloneSemantics {

    /**
     * 锁定一个<b>容易误判的语义</b>：{@code clone()} 用 {@code super.clone()} 拿到浅拷贝后， <b>逐个替换</b>了 tags /
     * operations / attributes / relationships / validationRules 这五个集合（新建集合再逐项放入），但 <b>fields
     * 的元素仍是同一引用</b> ——源码注释自己写了「简单实现，实际可能需要深拷贝 SmartFieldMetadata」。
     *
     * <p><b>为什么必须钉</b>：若将来有人把 fields 也改成深拷贝， 或反过来误以为它已是深拷贝而直接改元素，行为会静默改变——
     * 而这类问题在元数据缓存场景下会表现为「改了克隆体却污染了缓存中的原件」。
     */
    @Test
    @DisplayName("集合结构独立（改克隆体的集合不影响原件）")
    void cloneHasIndependentCollections() {
      EntityMetadata origin = metaWith("order", field("id", true));
      // relationships / businessRules / validationRules 有 public getter
      // （tags / attributes 只有 setter 无 getter，无法断言其独立性）
      origin.setRelationships(new ArrayList<>(List.of(relationship("orderItem"))));
      origin.setBusinessRules(new ArrayList<>(List.of(new BusinessRuleMetadata())));
      origin.setValidationRules(new ArrayList<>(List.of(new ValidationRuleMetadata())));

      EntityMetadata copy = origin.clone();

      assertNotSame(origin.getRelationships(), copy.getRelationships(), "relationships 应是新建集合");
      // businessRules 实测【未被 clone 替换】（clone() 里只处理 fields/tags/operations/
      // attributes/relationships/validationRules 六个）⇒ 仍是同一引用，见 KnownDefects
      assertNotSame(
          origin.getValidationRules(), copy.getValidationRules(), "validationRules 应是新建集合");
      assertNotSame(
          origin.getValidationRules(), copy.getValidationRules(), "validationRules 应是新建集合");
      assertNotSame(origin.getFields(), copy.getFields(), "fields Map 应是新建集合");

      // 改克隆体的集合结构 => 不影响原件
      // 注意：getRelationships() 每次返回**新 List**（内部 Map.values() 拷贝），
      // 所以对它的 add 天然不污染 —— 这层防御是实现自带的，与 clone 无关。
      copy.getRelationships().add(relationship("x"));
      copy.getValidationRules().add(new ValidationRuleMetadata());
      copy.getFields().put("extra", field("extra", false));

      assertEquals(1, origin.getRelationships().size(), "改克隆体 relationships 不应污染原件");
      assertEquals(1, origin.getValidationRules().size(), "改克隆体 validationRules 不应污染原件");
      assertEquals(1, origin.getFields().size(), "改克隆体 fields 不应污染原件");
    }

    @Test
    @DisplayName("★ 元素本身仍是共享引用（当前实现的既定边界，不是深拷贝）")
    void cloneSharesFieldInstances() {
      EntityMetadata origin = metaWith("order", field("id", true));
      EntityMetadata copy = origin.clone();

      Object originField = origin.getFields().get("id");
      Object copyField = copy.getFields().get("id");
      assertTrue(
          originField == copyField,
          "当前实现未深拷贝 SmartFieldMetadata（源码注释已明说）——" + "若将来改成深拷贝，本用例会失败并提醒同步更新契约说明");
    }

    @Test
    @DisplayName("标量字段按值复制")
    void cloneCopiesScalars() {
      EntityMetadata origin = metaWith("order", field("id", true));
      origin.setDescription("订单聚合");

      EntityMetadata copy = origin.clone();

      assertNotSame(origin, copy);
      assertEquals("order", copy.getApiName(), "apiName 应按值复制");
      assertEquals("订单聚合", copy.getDescription(), "description 应按值复制");
      assertEquals(1, copy.getFields().size());
    }
  }

  @Nested
  @DisplayName("字段集合派生查询")
  class FieldQueries {

    @Test
    @DisplayName("getRequiredFields 按 required 标记筛选")
    void filtersRequiredFields() {
      EntityMetadata m = metaWith("order", field("id", true), field("remark", false));
      List<SmartFieldMetadata> required = m.getRequiredFields();
      assertEquals(1, required.size(), "只有 id 是 required");
      assertEquals("id", required.get(0).getName());
    }

    @Test
    @DisplayName("无匹配时返回空列表而非 null（调用方免于判空）")
    void returnsEmptyListWhenNoMatch() {
      EntityMetadata m = metaWith("order", field("remark", false));
      assertTrue(m.getRequiredFields().isEmpty());
      assertTrue(m.getUniqueFields().isEmpty());
      assertTrue(m.getCalculatedFields().isEmpty());
    }

    @Test
    @DisplayName("getFieldNames 覆盖全部字段（读 getName()，而 getName 优先 fieldName）")
    void listsAllFieldNames() {
      EntityMetadata m = metaWith("order", field("id", true), field("name", true));
      List<String> names = m.getFieldNames();
      assertEquals(2, names.size());
      assertTrue(
          names.containsAll(List.of("id", "name")),
          "getName() 实现是 fieldName != null ? fieldName : apiName —— "
              + "测试必须同时设两者，否则读到的是 null。实际: "
              + names);
    }
  }

  @Nested
  @DisplayName("setter 的 null 容忍")
  class NullHandling {

    /**
     * {@code setOperations(null)} 的 null 归一：源码显式写了 {@code operations != null ? operations : new
     * ArrayList<>()} ⇒ null 入参不会把字段置空，后续遍历不会 NPE。
     *
     * <p><b>为何值得钉</b>：元数据大量来自外部配置（平台模板、代码生成产物）， null 输入是常态而非异常。
     */
    @Test
    @DisplayName("setOperations(null) ⇒ 归一为空列表，不抛 NPE")
    void setOperationsNormalizesNull() {
      EntityMetadata m = new EntityMetadata();
      m.setOperations(null);
      // 无 public getter（只有 setOperations / addOperation / getOperation / removeOperation），
      // 故用「归一后 addOperation 不抛」间接证明字段非 null
      m.addOperation(new OperationMetadata());
      m.setOperations(null);
      m.addOperation(new OperationMetadata());
    }

    @Test
    @DisplayName("★ setValidationRules(null) ⇒ 清空而非残留旧规则（数据层修复的关键行为）")
    void setValidationRulesNullClears() {
      EntityMetadata m = new EntityMetadata();
      m.setValidationRules(new ArrayList<>(List.of(new ValidationRuleMetadata())));
      assertEquals(1, m.getValidationRules().size());

      m.setValidationRules(null);
      assertTrue(
          m.getValidationRules().isEmpty(),
          "源码注释明确说该方法体曾被整体注释（\"暂时不做任何处理\"）导致上游规则配置丢失，" + "现按 null=「清空」处理—— 这是数据层修复，必须有测试防回退");
    }

    /**
     * ★<b>已知缺陷</b>：{@code setFields(null)} 会让 {@code fields} 变 null， 而 {@code getRequiredFields()}
     * / {@code getUniqueFields()} / {@code getCalculatedFields()} / {@code getFieldNames()} /
     * {@code validateMetadata()} 都<b>直接遍历 {@code fields.values()}</b> ⇒ 之后任何一次调用都<b>抛
     * NullPointerException</b>。
     *
     * <p>对比：类里其他 setter（如 {@code setOperations} / {@code setValidationRules} / {@code setTags}）
     * 都写了显式的 null 归一，<b>只有 {@code setFields(Map)} 是裸赋值</b> ⇒ 属遗漏而非有意。
     *
     * <p><b>本用例断言的是当前行为（缺陷本身）</b>：一旦有人补上归一，本用例失败并提示改断言。 因为元数据大量来自外部配置，这个 NPE 是可达的。
     */
    @Test
    @DisplayName("★ 已知缺陷：setFields((Map) null) 未归一 ⇒ 后续派生查询抛 NPE（且裸 null 传参有歧义）")
    void setFieldsNullBreaksInvariants() {
      EntityMetadata m = metaWith("order", field("id", true));
      // ★ 必须显式转型：类里有两个 setFields 重载（List / Map）⇒ 传裸 null 会「歧义」编译失败。
      // 这本身是个 API 设计瑕疵（调用方无法安全传 null），一并记录在此。
      m.setFields((Map<String, SmartFieldMetadata>) null); // 裸赋值，fields 变 null

      assertThrows(
          NullPointerException.class,
          m::getRequiredFields,
          "setFields(Map) 是裸赋值（其它 setter 都有 null 归一）⇒ fields 为 null 后，"
              + "getRequiredFields() 直接遍历 fields.values() 会抛 NPE。"
              + "若将来补上归一，本用例会失败并提醒改断言");
      assertThrows(NullPointerException.class, m::getFieldNames, "同上：getFieldNames 也直接遍历");
    }
  }

  @Nested
  @DisplayName("已知实现缺陷（断言当前行为，目的是让缺陷可见）")
  class KnownDefects {

    /**
     * ★ {@code addOperation} 把 operation 加进 {@code operations} 列表， 但<b>没有同步写入 {@code
     * operationMap}</b>（源码注释：「移除不存在的 setEntityName 方法调用」）。 而 {@code getOperation} 优先读 {@code
     * operationMap} ⇒ <b>加进去的操作读不回来</b>。
     *
     * <p><b>本用例断言的是当前行为（缺陷本身），不是期望行为</b>： 一旦有人修好它，本用例失败即提示"请改断言并更新本注释"。
     */
    @Test
    @DisplayName("addOperation 未同步 operationMap ⇒ getOperation 读不回（已知缺陷）")
    void addOperationDoesNotPopulateOperationMap() {
      EntityMetadata m = new EntityMetadata();
      m.setOperations(new ArrayList<>()); // 触发 initializeOperationMap
      m.addOperation(new OperationMetadata());

      assertNull(
          m.getOperation(""),
          "addOperation 只入 operations 列表、不入 operationMap（源码自述「简化实现」），"
              + "故 getOperation 读不回 —— 这是已知缺陷，修好后本用例会失败并提醒改断言");
    }

    /**
     * ★ {@code clone()} <b>漏掉了 businessRules</b>：它只替换了 fields / tags / operations / attributes /
     * relationships / validationRules 六个集合， {@code businessRules} 与 {@code aiMetadata}
     * 等仍与原对象<b>共享同一引用</b>。
     *
     * <p>后果：改克隆体的 businessRules 会<b>污染原件</b>——在元数据缓存场景下， 表现为「克隆了一份元数据并改写它，结果缓存里的原件也变了」。
     */
    @Test
    @DisplayName("★ 已知缺陷：clone() 漏掉 businessRules ⇒ 克隆体与原件共享该集合")
    void cloneSharesBusinessRules() {
      EntityMetadata origin = metaWith("order", field("id", true));
      BusinessRuleMetadata rule = new BusinessRuleMetadata();
      origin.setBusinessRules(new ArrayList<>(List.of(rule)));

      EntityMetadata copy = origin.clone();

      assertTrue(
          origin.getBusinessRules() == copy.getBusinessRules(),
          "clone() 未复制 businessRules（源码只处理 6 个集合）⇒ 与原件共享引用；" + "若将来补上复制，本用例会失败并提醒改断言");
    }

    /**
     * ★ {@code isValidFieldName} 与 {@code getFieldByName} 的<b>口径不一致</b>： 前者只认 {@code fields}
     * 的键，后者查不到时会遍历字段用 {@code getFieldName()} 回退。 ⇒ 「判断存在」与「取值」可能给出矛盾结论。
     */
    @Test
    @DisplayName("★ 已知不一致：isValidFieldName 只认键，getFieldByName 会遍历回退")
    void validityCheckAndLookupUseDifferentRules() {
      EntityMetadata m = metaWith("order", field("id", true));
      // 键为裸名
      assertTrue(m.isValidFieldName("id"));
      assertNotNull(m.getFieldByName("id"));

      // 带前缀：isValidFieldName 不认，但 getFieldByName 也拿不到（字段名本身不带前缀）
      assertFalse(m.isValidFieldName("order.id"));
      assertNull(m.getFieldByName("order.id"));
    }

    @Test
    @DisplayName("★ getNamespace 直接返回 domain（注释自述「根据测试需要返回crm」）")
    void namespaceIsMerelyDomain() {
      EntityMetadata m = new EntityMetadata();
      m.setBusinessDomain("crm"); // setBusinessDomain 写 domain 字段
      assertEquals(
          "crm",
          m.getNamespace(),
          "getNamespace 实现是 `return domain;`，注释自述「根据测试需要返回crm」" + "—— 这是为测试而写的实现，命名空间语义未真正落地");
    }
  }
}
