package com.bone.studio.generator.infrastructure.service;

import com.bone.studio.generator.domain.model.data.GenTableMetadata;
import org.springframework.stereotype.Component;

/**
 * 域仓储生成器：只产接口，不产实现类。
 *
 * <p>实现由 {@code @EnableSqlRepositories} 代理（blueprint {@code domain/repository/package-info.java}
 * 同口径）， 手写 {@code XxxRepositoryImpl} 违反持久化唯一约束 HC-001 / HC-006。
 */
@Component
public class RepositoryGenerator extends AbstractFileGenerator {

  public RepositoryGenerator(TemplateRenderer templateRenderer) {
    super(templateRenderer);
  }

  @Override
  protected String templateType() {
    return "repository";
  }

  @Override
  protected String directory(GenTableMetadata table) {
    return "/domain/repository/";
  }

  @Override
  protected String fileName(GenTableMetadata table) {
    return table.getCustomEntityName() + "Repository.java";
  }
}
