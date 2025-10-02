package com.bone.core.domain;

/**
 * @author renhui.trh
 */
public interface UseCase<T> {
    /**
     * 执行用例
    /*
     *
     * @param dto DTO
     * @param useCaseContext 用例上下文
     * @return DTO
     * @param <S>  DTO
     */
    <S extends T> S execute(S dto, UseCaseContext useCaseContext);
}
