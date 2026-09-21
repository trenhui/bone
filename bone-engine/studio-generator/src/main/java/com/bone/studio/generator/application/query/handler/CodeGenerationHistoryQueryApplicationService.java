package com.bone.studio.generator.application.query.handler;

import com.bone.studio.generator.application.query.qry.CodeGenerationHistoryQuery;
import com.bone.studio.generator.domain.history.CodeGenerationHistory;
import com.bone.studio.generator.domain.repository.CodeGenerationHistoryRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 查询代码生成历史记录（简写路径 /history）。 */
@Component
@RequiredArgsConstructor
public class CodeGenerationHistoryQueryApplicationService {

  private final CodeGenerationHistoryRepository historyRepository;

  @Transactional(readOnly = true)
  public List<CodeGenerationHistory> handle(CodeGenerationHistoryQuery qry) {
    return historyRepository.findAll();
  }
}
