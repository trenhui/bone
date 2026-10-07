package com.bone.blueprint.adapter.web.dto.request;

import com.bone.core.model.PageParam;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订单分页查询入参（adapter 层 HTTP 入参，对齐《Bone-DDD》E-13.1）。
 *
 * <p><b>为什么叫 Request 而不是 Qry</b>：本工程adapter 层分页入参统一以 {@code Request} 收尾（{@code CreateOrderReq}等）， 而
 * {@code Qry} 后缀在全工程仅此一处，属于孤例；且 {@code Qry} 与 application 层的 {@code OrderPageQuery} 只差一个字母，
 * 靠命名区分层次反而增加阅读负担。层次差异由包名（{@code adapter.web.dto.request} vs {@code application.query}）表达更准确。
 *
 * <p><b>分页字段继承自 {@link PageParam}</b>：{@code page}/{@code size}（含 {@code @Min(1)} 与
 * {@code @Max(100)}）是传输关注点，唯一真源在 {@code bone-core}；本类只保留<b>领域过滤条件</b>。 此前本类内联声明 {@code page}/{@code
 * size}，而 {@code PageParam} 当时无上限 ⇒ 两份定义需手工同步，上限约束必然漂移（现已把上限收口到 {@code PageParam}）。
 *
 * <p><b>生成器约定</b>：studio-generator的 {@code PageQueryGenerator} 以 {@code <Entity>PageRequest}
 * 为文件名模板， {@code pageQuery.ftl} 已同步为「继承 {@code PageParam} + 只写领域过滤条件」，本类是对齐基准； 改名时生成器模板与 {@code
 * GeneratedLayoutTest} 需同步。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderPageRequest extends PageParam {

  private Long customerId;

  private String status;
}
