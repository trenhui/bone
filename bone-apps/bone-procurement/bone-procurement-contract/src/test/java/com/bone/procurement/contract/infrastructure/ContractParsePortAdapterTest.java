package com.bone.procurement.contract.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.bone.aidoc.application.DocumentParseApplicationService;
import com.bone.aidoc.domain.gateway.DocumentParser;
import com.bone.aidoc.domain.gateway.ParsedDocument;
import com.bone.procurement.contract.domain.model.review.ContractParseFailedException;
import com.bone.procurement.contract.domain.model.review.ParsedClause;
import com.bone.procurement.contract.domain.model.review.ParsedContract;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 合同解析生产适配器：跨页条款锚点完整，解析失败 fail-closed。 */
class ContractParsePortAdapterTest {

  @Test
  void 跨页条款锚点完整不截断() {
    String page1 = "第一条 甲方应支付合同价款\n";
    String page2 = "并于验收后30日内付清。\n第二条 保密条款\n";
    DocumentParser fakeParser =
        new DocumentParser() {
          @Override
          public ParsedDocument parse(InputStream input, String sourceRef) {
            return ParsedDocument.of(2, List.of(page1, page2), "fake");
          }

          @Override
          public String provider() {
            return "fake";
          }
        };
    ContractParsePortAdapter adapter =
        new ContractParsePortAdapter(new DocumentParseApplicationService(fakeParser));

    ParsedContract ir =
        adapter.parse(new ByteArrayInputStream(new byte[0]), "t-1", "src://contract-1", "doc-1");

    assertThat(ir.pageCount()).isEqualTo(2);
    assertThat(ir.clauses()).hasSize(2);
    ParsedClause crossPage = ir.clauses().get(0);
    assertThat(crossPage.page()).isEqualTo(1);
    assertThat(crossPage.spanStart()).isEqualTo(0);
    assertThat(crossPage.spanEnd()).as("span 必须跨页累计，不能在第一页截断").isGreaterThan(page1.length());
    assertThat(crossPage.text()).contains("甲方应支付合同价款").contains("于验收后30日内付清");
    assertThat(crossPage.text()).doesNotContain("第二条");
  }

  @Test
  void 解析失败failClosed抛领域异常() {
    DocumentParseApplicationService delegate = mock(DocumentParseApplicationService.class);
    when(delegate.parse(any(InputStream.class), anyString(), anyString(), anyString()))
        .thenThrow(new IllegalStateException("encrypted pdf"));
    ContractParsePortAdapter adapter = new ContractParsePortAdapter(delegate);

    assertThatThrownBy(
            () ->
                adapter.parse(
                    new ByteArrayInputStream(new byte[0]), "t-1", "src://contract-2", "doc-2"))
        .isInstanceOf(ContractParseFailedException.class)
        .hasMessageContaining("encrypted pdf");
  }
}
