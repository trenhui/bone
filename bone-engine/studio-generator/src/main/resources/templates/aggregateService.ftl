package ${utils.getPackagePath(basePackage, moduleName)}.application;

import ${utils.getPackagePath(basePackage, moduleName)}.application.command.Create${table.customEntityName}Command;
import ${utils.getPackagePath(basePackage, moduleName)}.application.command.Create${child.customEntityName}Command;
import ${utils.getPackagePath(basePackage, moduleName)}.domain.model.${utils.toPackageSegment(table.customEntityName)}.${table.customEntityName};
import ${utils.getPackagePath(basePackage, moduleName)}.domain.model.${utils.toPackageSegment(child.customEntityName)}.${child.customEntityName};
import ${utils.getPackagePath(basePackage, moduleName)}.domain.repository.${table.customEntityName}Repository;
import ${utils.getPackagePath(basePackage, moduleName)}.domain.repository.${child.customEntityName}Repository;
import com.bone.core.annotation.NoDomainEvent;
import com.bone.core.exception.DomainException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ${table.tableComment!'主表'} - ${child.tableComment!'子表'} 主子聚合应用服务（一对多）。
 *
 * <p>由代码生成器基于表 ${table.originalTableName}（主）/ ${child.originalTableName}（子，外键列 ${fkColumn}）生成。
 * 一个用例一次事务内保存聚合根与全部子实体（子实体身份在构造期预分配，外键在创建时即指向主表 id），
 * 避免半成品聚合：主表写成功、明细写失败会整体回滚。
 *
 * <p><b>为何不用 SELECT ... FOR UPDATE</b>：悲观锁易死锁且多副本部署下行锁失效（AGENTS §一.7）；
 * 主子创建是低冲突写路径，靠主表预分配 id + 子表外键直接指向即可，无需加锁。
 *
 * <p><b>不发 DomainEvent（E-5.4 豁免一类：内部状态迁移）</b>：聚合创建无跨聚合协作需求。
 */
@Service
@RequiredArgsConstructor
@NoDomainEvent
public class ${table.customEntityName}AggregateApplicationService {

  private final ${table.customEntityName}Repository ${table.customEntityName?uncap_first}Repository;
  private final ${child.customEntityName}Repository ${child.customEntityName?uncap_first}Repository;

  /**
   * 一次事务创建${table.tableComment!'主表'}及其全部${child.tableComment!'明细'}。
   *
   * @param command 主表创建命令
   * @param itemCommands 子表创建命令列表，至少一条（空聚合的${child.tableComment!'明细'}没有业务意义）
   * @return 主表 id
   */
  @Transactional
  public Long createWithChildren(
      Create${table.customEntityName}Command command,
      List<Create${child.customEntityName}Command> itemCommands) {
    if (itemCommands == null || itemCommands.isEmpty()) {
      throw new DomainException("${child.tableComment!'明细'}不能为空，主子聚合至少需要一条子记录");
    }
    ${table.customEntityName} parent =
        ${table.customEntityName}.create(<#list parentColumns as column>command.${column.fieldName}()<#sep>, </#sep></#list>);
    ${table.customEntityName?uncap_first}Repository.save(parent);

    for (Create${child.customEntityName}Command itemCommand : itemCommands) {
      ${child.customEntityName} item =
          ${child.customEntityName}.create(<#list childColumns as column><#if column.originalColumnName == fkColumn>parent.getId()<#else>itemCommand.${column.fieldName}()</#if><#sep>, </#sep></#list>);
      ${child.customEntityName?uncap_first}Repository.save(item);
    }
    return parent.getId();
  }
}
