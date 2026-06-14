package com.bone.studio.generator.domain.service;

import com.bone.studio.generator.domain.code.GeneratedFile;
import com.bone.studio.generator.domain.data.CodeTemplate;
import com.bone.studio.generator.domain.data.GenTableMetadata;

public interface FileGenerator {
  boolean supports(String templateType);

  GeneratedFile generate(
      GenTableMetadata table, CodeTemplate template, String basePackage, String moduleName);
}
