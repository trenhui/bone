package ${utils.getPackagePath(basePackage, moduleName)}.application;

import ${utils.getPackagePath(basePackage, moduleName)}.application.command.Create${table.customEntityName}Command;
import ${utils.getPackagePath(basePackage, moduleName)}.application.command.Update${table.customEntityName}Command;
import ${utils.getPackagePath(basePackage, moduleName)}.application.query.dto.${table.customEntityName}Dto;
import ${utils.getPackagePath(basePackage, moduleName)}.domain.model.${utils.toPackageSegment(table.customEntityName)}.${table.customEntityName};
import ${utils.getPackagePath(basePackage, moduleName)}.domain.repository.${table.customEntityName}Repository;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.exception.BizException;
import com.bone.core.model.PageResult;
import com.bone.metadata.sdk.domain.exception.OptimisticLockingFailureException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ${table.tableComment!'实体'}应用服务。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。默认语义化 {@code *ApplicationService}，命令直接内联处理，
   * 不另起 CommandHandler / QueryHandler（ADR-0028：一个用例只选一种构件）。
 *
 * <p><b>不发 DomainEvent（E-5.4 豁免一类：内部状态迁移）</b>：CRUD 骨架无跨聚合协作需求。后续若要发布领域事件，
 * 去掉类上的 {@code @NoDomainEvent}、注入 {@code DomainEventPublisher} 并在写路径配对 {@code publishFrom(entity)}
 * ——只 {@code save} 不 {@code publishFrom} 会被 E-5.4 门禁拦下。
 *
 * <p><b>不用 BizException.of(message)</b>：其默认码是 500，会把「业务规则不满足」报成服务端故障、污染 SLO；
 * 这里一律显式给 HTTP 状态 + 稳定业务码（{@code ERROR_PREFIX}），供前端 {@code i18n.t('errors.' + errorCode)} 映射。
 */
@Service
@RequiredArgsConstructor
@NoDomainEvent
public class ${table.customEntityName}ApplicationService {

  /** 稳定业务码前缀：{@code <模块>_<聚合>}，BlueprintErrorCodes 的命名格式同样是 {@code 前缀_领域_原因}。 */
  private static final String ERROR_PREFIX = "${moduleName?upper_case}_${table.customEntityName?upper_case}";

  private final ${table.customEntityName}Repository repository;

  /** 创建${table.tableComment!'实体'}：身份在聚合构造期预分配（ADR-0019），返回新建 id。 */
  @Transactional
  public Long create(Create${table.customEntityName}Command command) {
    ${table.customEntityName} entity = ${table.customEntityName}.create(<#list businessColumns as column>command.${column.fieldName}()<#sep>, </#sep></#list>);
    repository.save(entity);
    return entity.getId();
  }

  /** 更新${table.tableComment!'实体'}：加载 → 聚合内改 → 保存（{@code update} 自带乐观锁与租户护栏）。 */
  @Transactional
  public void update(Update${table.customEntityName}Command command) {
    ${table.customEntityName} entity = require(command.id());
    entity.applyUpdate(<#list businessColumns as column>command.${column.fieldName}()<#sep>, </#sep></#list>);
    save(entity);
  }

  /** 删除${table.customEntityName}：租户护栏由 SDK 注入 {@code deleteById} 内联 SQL（ADR-0029）。 */
  @Transactional
  public void delete(Long id) {
    repository.deleteById(require(id).getId());
  }

  /** 查询详情。 */
  @Transactional(readOnly = true)
  public ${table.customEntityName}Dto getById(Long id) {
    return ${table.customEntityName}Dto.from(require(id));
  }

  /** 分页查询：租户过滤由 SDK 自动注入，仓储侧不重复拼 tenant 条件。 */
  @Transactional(readOnly = true)
  public PageResult<${table.customEntityName}Dto> page(int pageNum, int pageSize) {
    return repository.findPage(pageNum, pageSize).map(${table.customEntityName}Dto::from);
  }

  private ${table.customEntityName} require(Long id) {
    return Optional.ofNullable(repository.findById(id))
        .orElseThrow(
            () ->
                new BizException(
                    HttpStatus.NOT_FOUND.value(),
                    "${table.tableComment!'实体'}不存在: " + id,
                    ERROR_PREFIX + "_NOT_FOUND"));
  }

  /**
   * 并发冲突翻译：SDK 乐观锁失败必须翻译成 409，让前端可重试。
   *
   * <p>若不做这层翻译，{@code OptimisticLockingFailureException} 会落到全局异常处理器的兜底分支成为 5xx。
   */
  private void save(${table.customEntityName} entity) {
    try {
      repository.update(entity);
    } catch (OptimisticLockingFailureException ex) {
      throw new BizException(
          HttpStatus.CONFLICT.value(),
          "${table.tableComment!'实体'}已被其他请求修改，请重试",
          ERROR_PREFIX + "_CONFLICT",
          ex);
    }
  }
}
