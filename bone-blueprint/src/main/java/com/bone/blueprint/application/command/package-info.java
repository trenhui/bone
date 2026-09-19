/**
 * 命令对象（不可变 record，如 {@code CreateOrderCommand}）。
 *
 * <p>本模块采用 ADR-0028「Application Service First + Selective CQRS」：命令直接由 {@code *ApplicationService}
 * 内联处理，故本包只含命令对象，<strong>不含</strong> {@code command/handler/}——仅当某写意图需显式契约且存在多入口 （独立路由 / 异步 /
 * 多协议）时，才拆出 {@code *CommandHandler}。
 */
package com.bone.blueprint.application.command;
