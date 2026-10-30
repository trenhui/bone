package com.bone.metadata.sdk.metadata.client;

import com.bone.metadata.sdk.domain.model.AllocationContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按名称批量查扩展字段的请求体（{@code POST /api/v1/metadata/fields:searchByNames}）。
 *
 * <p><b>两个字段都必须显式约束</b>：下游 {@code findExtensionFieldsByNames} 要用 {@code ctx.getTenantId()} 拼缓存键，缺
 * context 时会 NPE ⇒ 表现为 <b>500</b>。而"{@code logicalNames} 为空"恰好会 在触及 context 前提前返回 {@code []} ⇒
 * 请求体只有 names 没有 context 时，空体探针（names 为 null）永远 走不到 NPE 分支，问题被结构性掩盖。因此必须在入站边界就把"缺 context"挡成
 * 400，而不是让它在下游炸成 500。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FieldsByNamesRequest {

  @NotNull(message = "查询上下文 context 不能为空")
  @Valid
  private AllocationContext context;

  @NotEmpty(message = "待查询字段名称列表 logicalNames 不能为空")
  private List<String> logicalNames;
}
