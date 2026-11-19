package com.bone.procurement.contract.domain.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.bone.procurement.contract.domain.model.ParsedClause;
import com.bone.procurement.contract.domain.model.ParsedContract;
import com.bone.procurement.contract.domain.model.ReviewRequest;
import com.bone.procurement.contract.domain.model.ReviewSuggestion;
import com.bone.procurement.contract.domain.model.ReviewTraceEntry;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 出站端口契约：三个端口均为纯接口，签名锁定采购域与 bone-ai-* 之间的边界。 */
class PortContractTest {

  @Test
  void 解析端口签名锁定() throws Exception {
    Method parse =
        ContractParsePort.class.getMethod(
            "parse", InputStream.class, String.class, String.class, String.class);
    assertThat(parse.getReturnType())
        .as("domain 端口返回自有 DTO，不得把 aidoc DocumentIR 泄漏进领域层")
        .isEqualTo(ParsedContract.class);

    assertThat(
            Arrays.stream(ParsedContract.class.getRecordComponents()).map(RecordComponent::getName))
        .containsExactly("docId", "tenantId", "sourceRef", "pageCount", "clauses", "partial");
    assertThat(
            Arrays.stream(ParsedClause.class.getRecordComponents()).map(RecordComponent::getName))
        .containsExactly("clauseNo", "text", "page", "spanStart", "spanEnd");
  }

  @Test
  void 审查与留痕端口签名锁定() throws Exception {
    assertThat(ContractReviewPort.class.getMethod("review", ReviewRequest.class).getReturnType())
        .isEqualTo(ReviewSuggestion.class);
    assertThat(ReviewTracePort.class.getMethod("append", ReviewTraceEntry.class).getReturnType())
        .isEqualTo(void.class);
    assertThat(ReviewTracePort.class.getMethod("findByReviewId", String.class).getReturnType())
        .isEqualTo(List.class);
  }

  @Test
  void 端口可被受控实现替换() {
    ContractParsePort parsePort =
        (input, tenantId, sourceRef, docId) ->
            new ParsedContract(docId, tenantId, sourceRef, 0, List.of(), false);
    ContractReviewPort reviewPort = request -> null;
    ReviewTracePort tracePort =
        new ReviewTracePort() {
          @Override
          public void append(ReviewTraceEntry entry) {}

          @Override
          public List<ReviewTraceEntry> findByReviewId(String reviewId) {
            return List.of();
          }
        };

    assertThat(parsePort).isInstanceOf(ContractParsePort.class);
    assertThat(reviewPort).isInstanceOf(ContractReviewPort.class);
    assertThat(tracePort.findByReviewId("rv-1")).isEmpty();
  }
}
