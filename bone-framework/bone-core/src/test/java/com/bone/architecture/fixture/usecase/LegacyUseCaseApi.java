package com.bone.architecture.fixture.usecase;

/**
 * 规则单测夹具：遗留 UseCase API（位于 com.bone.architecture.fixture.usecase 包； noBoneCoreUseCaseApiDependency
 * 的反例参考——依赖目标须为 com.bone.core.usecase.. 才命中）。
 */
public final class LegacyUseCaseApi {

  public static final String NAME = "legacy-usecase-api";

  private LegacyUseCaseApi() {}
}
