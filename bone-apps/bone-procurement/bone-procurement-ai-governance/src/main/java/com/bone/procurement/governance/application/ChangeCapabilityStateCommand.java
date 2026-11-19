package com.bone.procurement.governance.application;

/** 能力状态变更命令。操作人不接受自报，由应用服务从登录态端口取（审计红线）。 */
public record ChangeCapabilityStateCommand(String code, String reason) {}
