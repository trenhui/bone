package com.bone.claim.domain.repository;

import com.bone.claim.domain.model.reconciliation.ReconciliationDiff;
import com.bone.metadata.sdk.Repository;

/** 对账差异明细仓储（§11.7，append-only，仅追加写入）。 */
public interface ReconciliationDiffRepository extends Repository<ReconciliationDiff, Long> {}
