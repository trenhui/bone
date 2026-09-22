package com.bone.studio.generator.domain.service;

import com.bone.studio.generator.domain.model.code.GeneratedFile;
import com.bone.studio.generator.domain.model.data.CodeTemplate;
import com.bone.studio.generator.domain.model.data.GenTableMetadata;

public interface FileGenerator {
  boolean supports(String templateType);

  GeneratedFile generate(
      GenTableMetadata table, CodeTemplate template, String basePackage, String moduleName);
}
