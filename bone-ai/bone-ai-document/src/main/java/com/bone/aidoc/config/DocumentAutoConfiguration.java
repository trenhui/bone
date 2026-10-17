package com.bone.aidoc.config;

import com.bone.aidoc.application.DocumentParseService;
import com.bone.aidoc.domain.gateway.DocumentParser;
import com.bone.aidoc.infrastructure.parser.DoclingSidecarAdapter;
import com.bone.aidoc.infrastructure.parser.PdfBoxDocumentParser;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * bone-ai-document 自动配置。
 *
 * <p>Docling sidecar（属性开启时作为主解析器，替换 PDFBox）+ PDFBox 兜底默认 + 解析应用服务。 部署形态（§7 #2）运行时决定，故 Docling
 * 默认关闭，开启即生效。
 */
@AutoConfiguration
@EnableConfigurationProperties(DocumentProperties.class)
public class DocumentAutoConfiguration {

  @Bean
  @ConditionalOnProperty(name = "bone.ai.document.docling.enabled", havingValue = "true")
  @Primary
  public DocumentParser doclingDocumentParser(DocumentProperties props) {
    DocumentProperties.Docling d = props.getDocling();
    return new DoclingSidecarAdapter(d.getBaseUrl(), d.getTimeoutMs());
  }

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
