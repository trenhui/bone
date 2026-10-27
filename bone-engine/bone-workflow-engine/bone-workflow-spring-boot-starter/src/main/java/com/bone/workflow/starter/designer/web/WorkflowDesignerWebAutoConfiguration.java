package com.bone.workflow.starter.designer.web;

import com.bone.workflow.starter.designer.WorkflowDefinitionCommandService;
import com.bone.workflow.starter.designer.WorkflowDesignerAuthorizer;
import com.bone.workflow.starter.designer.WorkflowSimulationService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * 设计器 REST 层自动装配：仅在「Servlet Web 上下文」下注册控制器与异常建议。
 *
 * <p><b>为什么是条件装配</b>：starter 不假设宿主一定有 web 层。无 web 的宿主（纯领域库 / 测试）不会加载本配置， 也不会因 classpath 缺
 * spring-web 而启动失败——{@code @ConditionalOnClass(DispatcherServlet)} 确保「连 spring-web 都没有」
 * 时连配置类都不加载（避免触发控制器类的加载）。
 *
 * <p>两条条件缺一不可：{@code SERVLET} 保证是 Web MVC 环境（而非 reactive），{@code DispatcherServlet} 兜底 classpath
 * 缺失。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
public class WorkflowDesignerWebAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public WorkflowDesignerController workflowDesignerController(
      WorkflowDefinitionCommandService command,
      WorkflowSimulationService simulation,
      WorkflowDesignerAuthorizer authorizer) {
    return new WorkflowDesignerController(command, simulation, authorizer);
  }

  @Bean
  @ConditionalOnMissingBean
  public WorkflowDesignerExceptionAdvice workflowDesignerExceptionAdvice() {
    return new WorkflowDesignerExceptionAdvice();
  }
}
