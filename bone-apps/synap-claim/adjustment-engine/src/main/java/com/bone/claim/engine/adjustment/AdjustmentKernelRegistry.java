package com.bone.claim.engine.adjustment;

import com.bone.claim.sdk.adjustment.AdjustmentForm;
import com.bone.claim.sdk.adjustment.AdjustmentStrategy;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 责任形式 → 内核的枚举分派（唯一登记处）。
 *
 * <p><b>未知形式不回落</b>：{@link #require} 对未登记形式抛 {@link IllegalArgumentException}， 与 SDK 的 {@code
 * AdjustmentForm.of} 同口径。app 的分派器以本类为「当前支持矩阵」的真源， 避免「代码里说支持、文档里说不支持」这种两处口径分叉。
 */
public final class AdjustmentKernelRegistry {

  private static final Map<AdjustmentForm, AdjustmentStrategy> KERNELS = build();

  private AdjustmentKernelRegistry() {}

  private static Map<AdjustmentForm, AdjustmentStrategy> build() {
    Map<AdjustmentForm, AdjustmentStrategy> map = new EnumMap<>(AdjustmentForm.class);
    for (AdjustmentStrategy kernel :
        List.of(new ReimbursementKernel(), new FixedAmountKernel(), new AllowanceKernel())) {
      map.put(kernel.form(), kernel);
    }
    return Map.copyOf(map);
  }

  public static AdjustmentStrategy require(AdjustmentForm form) {
    if (form == null) {
      throw new IllegalArgumentException("责任形式为空");
    }
    AdjustmentStrategy kernel = KERNELS.get(form);
    if (kernel == null) {
      throw new IllegalArgumentException("未登记的责任形式：" + form);
    }
    return kernel;
  }

  /** 当前具备可执行金额语义的形式。非报销型登记了但不支持，调用它们会 fail-closed。 */
  public static boolean supports(AdjustmentForm form) {
    return form == AdjustmentForm.REIMBURSEMENT;
  }

  public static List<AdjustmentForm> registeredForms() {
    return List.copyOf(KERNELS.keySet());
  }
}
