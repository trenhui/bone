package com.bone.masterdata.application.query.qry;

import lombok.Data;

/**
 * 供应商合作资格的分页查询条件。
 *
 * <p><b>不含 tenantId</b>：租户一律取登录态（{@code CurrentUserPort}），查询参数里放租户就等于把隔离交给调用方自报。 与平台其它分页查询 （{@code
 * MasterDataEntityPageQuery} 等）保持 {@code page} 从 1 起、{@code size} 默认 10 的同构口径。
 */
@Data
public class SupplierPageQuery {

  private int page = 1;

  private int size = 10;
}
