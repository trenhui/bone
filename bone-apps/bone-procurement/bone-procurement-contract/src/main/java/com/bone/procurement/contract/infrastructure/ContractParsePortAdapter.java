package com.bone.procurement.contract.infrastructure;

import com.bone.aidoc.application.DocumentParseApplicationService;
import com.bone.aidoc.domain.model.DocumentIR;
import com.bone.procurement.contract.application.ConditionalOnContractAiReviewEnabled;
import com.bone.procurement.contract.domain.exception.ContractParseFailedException;
import com.bone.procurement.contract.domain.gateway.ContractParsePort;
import com.bone.procurement.contract.domain.model.ParsedClause;
import com.bone.procurement.contract.domain.model.ParsedContract;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 合同解析出站适配器：委托 bone-ai-document 的解析与跨页切分；任何解析失败一律 fail-closed。
 *
 * <p>本适配器是 aidoc 类型进入本上下文的<strong>唯一防腐层</strong>：DocumentIR 在这里转换为 domain 自有 ParsedContract，domain
 * 层零跨上下文依赖。
 */
@Component
@ConditionalOnContractAiReviewEnabled
public class ContractParsePortAdapter implements ContractParsePort {

  private final DocumentParseApplicationService delegate;

  public ContractParsePortAdapter(DocumentParseApplicationService delegate) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
  }

  @Override
  public ParsedContract parse(InputStream input, String tenantId, String sourceRef, String docId) {
    try {
      DocumentIR ir = delegate.parse(input, tenantId, sourceRef, docId);
      if (ir == null) {
        throw new ContractParseFailedException("合同解析结果为空: sourceRef=" + sourceRef);
      }
      return toParsedContract(ir);
    } catch (ContractParseFailedException e) {
      throw e;
    } catch (RuntimeException e) {
      throw new ContractParseFailedException(
          "合同解析失败: sourceRef=" + sourceRef + ", reason=" + e.getMessage(), e);
    }
  }

  /** 防腐层转换：只拷贝审查编排需要的字段，aidoc 类型不越过 infrastructure 边界。 */
  private static ParsedContract toParsedContract(DocumentIR ir) {
    List<ParsedClause> clauses =
        ir.clauses() == null
            ? List.of()
            : ir.clauses().stream()
                .map(
                    clause ->
                        ParsedClause.of(
                            clause.clauseNo(),
                            clause.text(),
                            clause.page(),
                            clause.spanStart(),
                            clause.spanEnd()))
                .toList();
    return ParsedContract.of(
        ir.docId(), ir.tenantId(), ir.sourceRef(), ir.pageCount(), clauses, ir.partial());
  }
}
