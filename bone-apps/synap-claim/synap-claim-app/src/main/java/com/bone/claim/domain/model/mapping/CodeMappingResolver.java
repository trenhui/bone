package com.bone.claim.domain.model.mapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 解析内核：保司码 → 标准码。纯函数，不依赖仓储，因此可以逐条变异验证。
 *
 * <p><b>裁决顺序是有讲究的，不能重排</b>：
 *
 * <ol>
 *   <li>空码直接未命中——空串永远不是合法编码，任何"兜底"都会把错误变成默认值。
 *   <li>拆分父码先判：即使有一条指向父码的映射，它也没有单一目标，必须先 fail-closed。
 *   <li>先按 ACTIVE 候选数分流，<b>再</b>看消歧键：先看键会把"同码多条但键都为空"误判成可解析。
 *   <li>最后才 canonicalize：标准码被合并后，旧映射仍应解析到新码；顺序反了会把"别名链断"当未命中。
 * </ol>
 *
 * <p><b>不做模糊兜底</b>：名称相似度、拼音、编辑距离只用于<b>离线出候选</b>（人工确认后落库）， 不进解析路径。上线期自动模糊匹配等于把"猜"写进赔款计算。
 */
public final class CodeMappingResolver {

  /** 别名链最大深度：够覆盖"多次更名"，又能让成环快速失败而不是无限循环。 */
  public static final int MAX_ALIAS_DEPTH = 8;

  private CodeMappingResolver() {}

  /**
   * @param rawCode 保司侧原始码值
   * @param disambiguationKey 收单期由属性确定性算出的消歧键（无则 null/空）
   * @param candidates 该 (保司, 域, 保司码) 下的候选映射（允许包含非 ACTIVE，本方法内部过滤）
   * @param aliases 该域下的标准码别名（允许包含非 ACTIVE）
   * @param splitParent 该标准码（或该保司码解析出的标准码）是否为拆分父码
   */
  public static MappingResolution resolve(
      String rawCode,
      String disambiguationKey,
      List<CodeMapping> candidates,
      List<StdCodeAlias> aliases,
      boolean splitParent) {
    if (rawCode == null || rawCode.isBlank()) {
      return MappingResolution.missing(MappingResolution.RAW_CODE_BLANK);
    }
    if (splitParent) {
      return MappingResolution.split();
    }

    List<CodeMapping> actives = new ArrayList<>();
    if (candidates != null) {
      for (CodeMapping candidate : candidates) {
        if (candidate != null
            && candidate.isActive()
            && rawCode.trim().equals(Objects.toString(candidate.getInsurerItemCode(), "").trim())) {
          actives.add(candidate);
        }
      }
    }
    if (actives.isEmpty()) {
      return MappingResolution.missing(MappingResolution.NO_ACTIVE_MAPPING);
    }
    if (actives.size() == 1) {
      return canonicalize(actives.get(0), aliases);
    }

    String key = disambiguationKey == null ? "" : disambiguationKey.trim();
    if (key.isEmpty()) {
      // 同码多条却没给消歧属性：宁可转人工，也不能按插入顺序挑一条
      return MappingResolution.ambiguous(MappingResolution.DISAMBIGUATION_REQUIRED);
    }
    CodeMapping matched = null;
    for (CodeMapping candidate : actives) {
      if (key.equals(Objects.toString(candidate.getDisambiguationKey(), ""))) {
        if (matched != null) {
          return MappingResolution.ambiguous(MappingResolution.DISAMBIGUATION_REQUIRED);
        }
        matched = candidate;
      }
    }
    if (matched == null) {
      return MappingResolution.missing(MappingResolution.DISAMBIGUATION_NOT_MATCHED);
    }
    return canonicalize(matched, aliases);
  }

  private static MappingResolution canonicalize(CodeMapping mapping, List<StdCodeAlias> aliases) {
    String current = mapping.getStandardCode();
    if (current == null || current.isBlank()) {
      // 库里出现空标准码属于数据损坏，按未命中处理（不可返回空串）
      return MappingResolution.missing(MappingResolution.NO_ACTIVE_MAPPING);
    }
    Map<String, String> next = new HashMap<>();
    if (aliases != null) {
      for (StdCodeAlias alias : aliases) {
        if (alias != null && alias.isActive()) {
          next.put(alias.getAliasCode(), alias.getCanonicalCode());
        }
      }
    }
    for (int hop = 0; hop < MAX_ALIAS_DEPTH; hop++) {
      String target = next.get(current);
      if (target == null) {
        return MappingResolution.resolved(current, mapping.getId());
      }
      if (target.isBlank()) {
        return MappingResolution.missing(MappingResolution.NO_ACTIVE_MAPPING);
      }
      current = target;
    }
    // 走出最大深度仍在上链 ⇒ 成环或链过长，交人工修数据
    return MappingResolution.aliasCycle();
  }
}
