package com.bone.workflow.starter.designer.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bone.workflow.starter.designer.WorkflowSimulationService;
import java.lang.reflect.Constructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

/**
 * 设计器控制器的<b>构造裁决</b>回归护栏（2026-10-27 联调实测缺陷）。
 *
 * <p><b>缺陷复现</b>：本控制器有两个构造——三参生产构造 + 包级两参「仅供测试」构造，<b>两者都非无参</b>。 在没有任何 {@code @Autowired}
 * 标注时，Spring 的构造推断 （{@link
 * AutowiredAnnotationBeanPostProcessor#determineCandidateConstructors}）无法裁决，退化成「找无参构造」， 最终抛 {@code
 * No default constructor found}。
 *
 * <p><b>为什么只在组合根暴露</b>：设计器自动配置 {@code WorkflowDesignerWebAutoConfiguration} 是用 {@code @Bean} 方法显式
 * {@code new} 的，永远走三参构造；但 {@code bone-boot} 组合根里 {@code MetadataApplication}（扫 {@code
 * com.bone.metadata}）会命中 engine starter 的 {@code @SpringBootApplication(scanBasePackages =
 * "com.bone")} 兜底全扫，把本类当普通 bean 扫进来 ⇒ 整应用启动失败。故护栏必须钉在「构造裁决」这一层，而不是「自动化配置注册」那一层。
 *
 * <p><b>变异验证</b>：摘掉生产构造上的 {@code @Autowired} ⇒ {@code determineCandidateConstructors}
 * 返回空数组，本测试转红（{@code expected: <1> but was: <0>}）。这是唯一能拦住回归的判据层面。
 */
class WorkflowDesignerControllerConstructorTest {

  private static Constructor<?>[] determineConstructors() {
    AutowiredAnnotationBeanPostProcessor bpp = new AutowiredAnnotationBeanPostProcessor();
    bpp.setBeanFactory(new DefaultListableBeanFactory());
    return bpp.determineCandidateConstructors(
        WorkflowDesignerController.class, "workflowDesignerController");
  }

  @Test
  @DisplayName("Spring 必须能唯一裁决出三参生产构造——两构造都非无参，缺 @Autowired 即启动失败")
  void springResolvesExactlyTheProductionConstructor() {
    Constructor<?>[] ctors = determineConstructors();
    assertNotNull(ctors, "构造推断返回 null ⇒ Spring 会退化成找无参构造并抛 No default constructor found");
    assertEquals(1, ctors.length, "必须唯一裁决，不能是 0（无解）也不能是 2（歧义）");
    assertEquals(3, ctors[0].getParameterCount(), "必须是三参生产构造，不能落到两参的测试便捷构造");
  }

  @Test
  @DisplayName("生产构造带 @Autowired——这是多构造场景下唯一的显式裁决依据")
  void productionConstructorIsAnnotatedAutowired() throws Exception {
    Constructor<?> prod =
        WorkflowDesignerController.class.getDeclaredConstructor(
            com.bone.workflow.starter.designer.WorkflowDefinitionCommandService.class,
            WorkflowSimulationService.class,
            com.bone.workflow.starter.designer.WorkflowDesignerAuthorizer.class);
    assertNotNull(
        prod.getAnnotation(org.springframework.beans.factory.annotation.Autowired.class),
        "生产构造缺 @Autowired ⇒ 与测试便捷构造形成歧义，组合根扫描时应用起不来");
  }
}
