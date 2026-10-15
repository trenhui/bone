package com.bone.aidoc.config;

import com.bone.aidoc.application.DocumentParseService;
import com.bone.aidoc.domain.gateway.DocumentParser;
import com.bone.aidoc.infrastructure.parser.PdfBoxDocumentParser;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** bone-ai-document 自动配置：默认 PDFBox 解析器 + 解析应用服务。 */
@AutoConfiguration
public class DocumentAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public DocumentParser documentParser() {
    return new PdfBoxDocumentParser();
  }

  @Bean
  @ConditionalOnMissingBean
  public DocumentParseService documentParseService(DocumentParser documentParser) {
    return new DocumentParseService(documentParser);
  }
}
