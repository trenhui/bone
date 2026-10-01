package com.bone.metadata.engine.runtime;

/**
 * 建模字段与物理列映射。
 *
 * <p>机制 A（领域固定列）：{@code physicalColumn == null}，运行期物理列名回退到 {@code code}。
 *
 * <p>机制 B（预留列扩展）：{@code physicalColumn} 指向宿主表上的 {@code ext_*} 物理列（由 SDK ColumnAllocator 分配），
 * 逻辑字段编码（code）与物理列名解耦。
 */
public record RuntimeFieldColumn(
    /** 字段编码（逻辑名；机制 A 时同时是物理列名） */
    String code,
    /** 声明类型（STRING/INTEGER/LONG/DECIMAL/BOOLEAN/DATETIME/...），用于运行期写入校验 */
    String type,
    /** 是否必填（写前校验） */
    boolean required,
    /** 是否唯一（写前查重） */
    boolean unique,
    /** 是否主键 */
    boolean primaryKey,
    /** 预留列物理列名（机制 B 指向 ext_*）；为 null 时回退到 code（机制 A） */
    String physicalColumn) {

  /** 兼容构造：机制 A（无预留列），物理列名回退到 code。 */
  public RuntimeFieldColumn(
      String code, String type, boolean required, boolean unique, boolean primaryKey) {
    this(code, type, required, unique, primaryKey, null);
  }

  /** 解析运行期真实物理列名：机制 B 用 physicalColumn，否则回退 code。 */
  public String physical() {
    return physicalColumn != null ? physicalColumn : code;
  }
}
