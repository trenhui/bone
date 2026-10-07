package ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.dto.request;

import com.bone.core.model.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * ${table.tableComment!'实体'}分页查询入参。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。分页入参收成一个对象而非散落的 {@code @RequestParam}：
 * 对齐 blueprint {@code OrderPageRequest} 的 {@code @Valid @ModelAttribute} 写法。
 *
 * <p><b>分页字段继承自 {@link PageParam}</b>（{@code page}/{@code size}，含 {@code @Min(1)} 与
 * {@code @Max(100)} 上限），本类只声明<b>领域过滤条件</b>（{@code customerId}/{@code status} 那类）——
 * 分页是传输关注点，应有唯一真源，不在每层重声明（否则两份定义需手工同步，上限约束必然漂移）。
 * Lombok {@code @Data} + {@code @EqualsAndHashCode(callSuper = true)} 保证父类字段参与 equals/hashCode。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ${table.customEntityName}PageRequest extends PageParam {

  // ===== 领域过滤条件（分页字段请勿在此重复声明，一律继承自 PageParam）=====
}
