package com.bone.claim.application;

import com.bone.claim.domain.model.sign.valueobject.SignCaseOutcome;

/** 一条签收明细的自动建案结果（应用层内部值，不直出适配器）。 */
public record ClaimSignCaseCreation(
    SignCaseOutcome outcome, Long claimId, String claimNo, String message) {}
