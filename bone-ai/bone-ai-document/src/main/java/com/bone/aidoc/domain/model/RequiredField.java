package com.bone.aidoc.domain.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 票种模板声明的必填关键字段（ADR-0046 §四：字段级可信度 + 按票种分别校验）。
 *
 * <p>{@code aliases} 是同一业务字段在不同 OCR 引擎 schema 下的命名（含中文列名）。匹配统一经 {@link
 * ClaimDocumentTemplate#normalize(String)} 归一化后<b>精确比对</b>，不做子串包含匹配——避免把 {@code start_date} 误认作
 * {@code invoice_date} 一类的误报（误报会让使用者关掉整道闸门）。
 *
 * @param canonicalName 统一字段 Schema 中的规范名（缺失证据按此名生成）
 * @param aliases 同义字段名（构造时自动包含规范名本身）
 * @param risk 该字段在此票种下的风险等级（模板声明优先于全局启发式）
 */
public record RequiredField(String canonicalName, Set<String> aliases, RiskLevel risk) {

  public RequiredField {
    if (canonicalName == null || canonicalName.isBlank()) {
      throw new IllegalArgumentException("必填字段规范名不可为空");
    }
    if (risk == null) {
      throw new IllegalArgumentException("必填字段风险等级不可为空: " + canonicalName);
    }
    aliases = aliases == null ? Set.of() : Set.copyOf(aliases);
  }

  /** 便捷构造：规范名 + 同义名（自动并入规范名）。 */
  public static RequiredField of(String canonicalName, RiskLevel risk, String... aliases) {
    Set<String> all = new LinkedHashSet<>();
    all.add(canonicalName);
    if (aliases != null) {
      Collections.addAll(all, aliases);
    }
    return new RequiredField(canonicalName, all, risk);
  }

  /** 是否已命中该字段（入参须为已归一化的字段名）。 */
  public boolean matches(String normalizedName) {
    if (normalizedName == null || normalizedName.isEmpty()) {
      return false;
    }
    if (ClaimDocumentTemplate.normalize(canonicalName).equals(normalizedName)) {
      return true;
    }
    for (String alias : aliases) {
      if (ClaimDocumentTemplate.normalize(alias).equals(normalizedName)) {
        return true;
      }
    }
    return false;
  }
}
