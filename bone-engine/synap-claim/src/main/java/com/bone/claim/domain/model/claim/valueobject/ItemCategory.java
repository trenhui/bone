package com.bone.claim.domain.model.claim.valueobject;

/** 费用类别（claim_item.category）。 */
public enum ItemCategory {
  DRUG,
  SERVICE,
  EXAM,
  MATERIAL;

  /** DB name → 枚举（SDK String 持久化口径）。 */
  public static ItemCategory ofName(String name) {
    for (ItemCategory v : values()) {
      if (v.name().equals(name)) {
        return v;
      }
    }
    throw new IllegalArgumentException("未知ItemCategory: " + name);
  }
}
