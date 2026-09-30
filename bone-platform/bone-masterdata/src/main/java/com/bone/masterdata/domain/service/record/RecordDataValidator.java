package com.bone.masterdata.domain.service.record;

import com.bone.masterdata.domain.model.entity.MasterDataField;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 主数据记录 data 的字段定义校验器（领域服务）。
 *
 * <p><b>为何存在</b>：修复「字段定义是死的」这一核心割裂——建模时声明的必填/类型/长度/值域， 在记录写入时必须真正拦截脏数据，而不是等到事后异步质检才发现。真实企业 MDM 的第一道
 * 防线是写入时校验，异步质检只是第二道。
 *
 * <p>本类为零框架依赖的纯领域服务：值域允许值由应用层按 field code 关联参考数据后传入， 领域层不感知参考数据的持久化形态。
 */
public final class RecordDataValidator {

  /**
   * 按字段定义校验记录数据。
   *
   * @param fields 实体已建模字段（为空表示尚未建模，不做校验以免阻断采集）
   * @param data 记录 data 已解析的键值对
   * @param allowedValuesByCode field code → 值域允许值集合；为空表示不校验值域
   * @return 违规描述列表；空列表表示通过
   */
  public List<String> validate(
      List<MasterDataField> fields,
      Map<String, Object> data,
      Map<String, Set<String>> allowedValuesByCode) {
    List<String> violations = new ArrayList<>();
    if (fields == null || fields.isEmpty()) {
      return violations;
    }
    Map<String, Object> safeData = data == null ? Map.of() : data;
    for (MasterDataField field : fields) {
      String code = field.getCode() != null ? field.getCode().value() : null;
      if (code == null) {
        continue;
      }
      Object value = safeData.get(code);
      String violation = field.validateValue(value);
      if (violation != null) {
        violations.add(violation);
        continue;
      }
      Set<String> allowed = allowedValuesByCode == null ? null : allowedValuesByCode.get(code);
      if (allowed == null || allowed.isEmpty() || value == null) {
        continue;
      }
      String text = String.valueOf(value).trim();
      if (!allowed.contains(text)) {
        violations.add("字段[" + code + "]取值 " + text + " 不在值域允许范围内");
      }
    }
    return violations;
  }
}
