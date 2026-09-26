package com.bone.system.domain.service;

import com.bone.system.domain.model.dict.SysDictItem;
import java.util.List;
import java.util.Optional;

/**
 * 字典下拉数据源缓存端口。
 *
 * <p><b>为什么要缓存</b>：字典是「每次打开表单都要读」的热点数据，而它几乎不改。v1 每次下拉都直查 DB，字典一多就把连接池浪费在重复读上。
 *
 * <p><b>为什么按类型失效而不是按项</b>：覆盖模型下一次查询要合并平台行与租户行，项的增删对 结果的影响是「整个类型」级别的；按项做细粒度失效只会带来一致性风险，收益为零。
 *
 * <p><b>缓存键为什么要带语言</b>：本地化标签是读侧算出来的（项表 + 译文表合并）， 不同语言的结果不能共用同一份缓存。
 */
public interface DictOptionsCache {

  /**
   * 读取缓存。
   *
   * @param parentCode 顶层传 {@code null}
   * @param language 为空表示不本地化
   */
  Optional<List<SysDictItem>> get(
      long tenantId, String typeCode, String parentCode, String language);

  void put(
      long tenantId, String typeCode, String parentCode, String language, List<SysDictItem> items);

  /** 失效某类型的全部缓存项（含所有父级与语言维度）。 */
  void evictType(String typeCode);

  void clear();
}
