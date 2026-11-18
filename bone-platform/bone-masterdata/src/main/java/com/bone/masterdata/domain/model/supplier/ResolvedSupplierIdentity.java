package com.bone.masterdata.domain.model.supplier;

import com.bone.masterdata.domain.model.identity.EntityKeyMapping;

/**
 * 身份解析结果。
 *
 * @param partyId 统一身份号
 * @param pendingRegistration 仅在「本地编码首次登记」时非空；调用方必须在业务裁决成立后才允许落库，裁决失败时必须丢弃， 否则下次同本地键会被错绑到未成立的主体。
 */
public record ResolvedSupplierIdentity(String partyId, EntityKeyMapping pendingRegistration) {}
