package ${utils.getPackagePath(basePackage, moduleName)}.application;

import com.bone.core.model.PageResult;
import ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.dto.response.${table.customEntityName}Response;
import ${utils.getPackagePath(basePackage, moduleName)}.domain.model.${utils.toPackageSegment(table.customEntityName)}.${table.customEntityName};
import ${utils.getPackagePath(basePackage, moduleName)}.domain.repository.${table.customEntityName}Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ${table.tableComment!'实体'}应用服务。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。简单读写走本服务，不生成 CommandHandler / QueryHandler。
 */
@Service
@RequiredArgsConstructor
public class ${table.customEntityName}ApplicationService {

  private final ${table.customEntityName}Repository repository;

  @Transactional(readOnly = true)
  public ${table.customEntityName}Response get(Long id) {
    return ${table.customEntityName}Response.from(repository.findById(id));
  }

  @Transactional(readOnly = true)
  public PageResult<${table.customEntityName}Response> page(int page, int size) {
    return repository.findPage(page, size).map(${table.customEntityName}Response::from);
  }
}
