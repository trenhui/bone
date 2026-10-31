package com.bone.metadata.runtime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bone.metadata.engine.runtime.MetadataErrorCodes;
import com.bone.metadata.engine.runtime.PublishedRuntimeEntity;
import com.bone.metadata.engine.runtime.RuntimeFieldColumn;
import com.bone.metadata.engine.runtime.RuntimeRecordException;
import com.bone.metadata.engine.runtime.RuntimeRelationSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 子实体码未声明时必须是 <b>404（{@link MetadataErrorCodes#RUNTIME_CHILD_RELATION_NOT_FOUND}）</b>， 不是
 * 500（{@link MetadataErrorCodes#RUNTIME_RELATION_CONFIG_INVALID}）。
 *
 * <p><b>为什么这条用例必须有</b>：2026-10-31 全模块联调实测，{@code GET
 * /runtime/entities/{entityCode}/records/{id}/children/{childEntityCode}} 在调用方传错子实体码 （甚至只是把父记录 id
 * 当成子实体码）时一律返回 <b>500</b>。修复主体是调用方（换个子实体码即可）， 报 500 会把「客户端输入错误」混进服务端故障监控、前端也只能弹「服务器错误」。 {@code
 * RUNTIME_RELATION_CONFIG_INVALID → 500} 只保留给<b>声明存在但配置非法</b>的建模侧错误。
 */
class RuntimeChildRelationNotFoundTest {

  private static PublishedRuntimeEntity parentWithChild(String childEntityCode) {
    RuntimeFieldColumn child =
        new RuntimeFieldColumn(
            "items",
            "CHILD_TABLE",
            false,
            false,
            false,
            null,
            null,
            "{\"childTable\":{\"childEntityCode\":\""
                + childEntityCode
                + "\",\"foreignKey\":\"claim_id\"}}");
    return new PublishedRuntimeEntity(
        "expense_reimbursement", "t_expense_reimbursement", "id", 1L, List.of(child));
  }

  private static RuntimeRelationApplicationService service() {
    // requireChildSpec 只依赖入参，不触碰任何协作者；用 null 构造即可隔离出「状态码归属」这一件事
    return new RuntimeRelationApplicationService(null, null, null, null, null, null);
  }

  @Test
  void declaredChildEntityCode_resolvesSpec() {
    RuntimeRelationSpec spec =
        service()
            .requireChildSpec(
                parentWithChild("expense_reimbursement_item"), "expense_reimbursement_item");
    assertThat(spec.childEntityCode()).isEqualTo("expense_reimbursement_item");
  }

  @Test
  void undeclaredChildEntityCode_is404_not500() {
    // 断言的是「错误码」而非 HTTP 数字：状态码由 GlobalExceptionHandler 按错误码映射，
    // 只断言数字会在另一处漂移时漏掉；这里锚定语义源头。
    assertThatThrownBy(
            () ->
                service()
                    .requireChildSpec(
                        parentWithChild("expense_reimbursement_item"), "purchase_order_item"))
        .isInstanceOf(RuntimeRecordException.class)
        .extracting(e -> ((RuntimeRecordException) e).getErrorCode())
        .isEqualTo(MetadataErrorCodes.RUNTIME_CHILD_RELATION_NOT_FOUND);

    // 显式反向断言：不得再退回「建模侧配置非法」的 500 码
    assertThatThrownBy(
            () ->
                service()
                    .requireChildSpec(parentWithChild("expense_reimbursement_item"), "bogus_child"))
        .isInstanceOf(RuntimeRecordException.class)
        .extracting(e -> ((RuntimeRecordException) e).getErrorCode())
        .isNotEqualTo(MetadataErrorCodes.RUNTIME_RELATION_CONFIG_INVALID);
  }
}
