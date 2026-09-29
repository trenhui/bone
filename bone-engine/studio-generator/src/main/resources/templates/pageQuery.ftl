package ${utils.getPackagePath(basePackage, moduleName)}.adapter.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * ${table.tableComment!'实体'}分页查询入参。
 *
 * <p>由代码生成器基于表 ${table.originalTableName} 生成。分页入参收成一个对象而非散落的 {@code @RequestParam}：
 * 对齐 blueprint {@code OrderPageQry} 的 {@code @Valid @ModelAttribute} 写法，边界值由 {@code @Min/@Max} 兜住，
 * 避免 {@code pageSize=Integer.MAX_VALUE} 直接打满数据库。
 */
@Data
public class ${table.customEntityName}PageQry {

  @Min(value = 1, message = "页码必须大于等于1")
  private Integer pageNum = 1;

  @Min(value = 1, message = "每页大小必须大于等于1")
  @Max(value = 100, message = "每页大小不能超过100")
  private Integer pageSize = 10;
}
