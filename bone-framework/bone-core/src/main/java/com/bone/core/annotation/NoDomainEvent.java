package com.bone.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * 标记 application 层的 CommandHandler / ApplicationService / EventHandler 在调用 Repository.save() 时
 * <b>不</b>需要配套 publishFrom()。
 *
 * <p><b>使用条件</b>：类中所有 save() 对应的状态迁移必须全部落在规范 E-5.4 定义的三类豁免之内： 内部状态迁移 / 终态到达 / 技术中间态。
 *
 * <p><b>要求</b>：被此注解标记的聚合方法本身必须在 JavaDoc 中注明 {@code 不发 DomainEvent} 的具体理由 （E-5.4 豁免注释放置位置与格式）。
 */
@Target(ElementType.TYPE)
public @interface NoDomainEvent {}
