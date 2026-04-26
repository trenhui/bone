package com.bone.tpa.sdk.identityRule;

public interface BizidentityHook<T,R> {
    /**
     * 所属领域英文： claim,invoice 等
     *
     * @return
     */
    String getDomain();

    /**
     *  所属领域中文描述： 发票，赔案
     * @return
     */
    String getDomainDesc();

    /**
     * 领域内的触发时机的code
     * @return
     */
    String getBeanType();

    /**
     * 领域内触发时机的描述
     * @return
     */
    String getBeanTypeDesc();

    /**
     * 执行的beanName
     * @return
     */
    String getBeanName();

    /**
     * 对bean 的功能详细描述（页面会展示）
     * @return
     */
    String getBeanDesc();


    R doEvent(T param);
}
