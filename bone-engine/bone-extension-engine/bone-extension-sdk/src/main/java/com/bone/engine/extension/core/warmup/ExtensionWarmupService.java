package com.bone.engine.extension.core.warmup;

import com.bone.engine.extension.api.spi.ExtensionPointRouter;
import com.bone.engine.extension.api.spi.ExtensionRepository;
import com.bone.engine.extension.support.context.BizContext;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * 扩展点预热服务
 *
 * <p>在应用启动时预热扩展点，避免首次调用冷启动
 *
 * @since 1.0.0
 */
@Slf4j
@Component
public class ExtensionWarmupService implements ApplicationListener<ContextRefreshedEvent> {

  private final ExtensionRepository extensionRepository;
  private final ExtensionPointRouter extensionPointRouter;
  private final ExecutorService executorService;

  @Autowired
  public ExtensionWarmupService(
      ExtensionRepository extensionRepository, ExtensionPointRouter extensionPointRouter) {
    this.extensionRepository = extensionRepository;
    this.extensionPointRouter = extensionPointRouter;
    this.executorService = Executors.newFixedThreadPool(5);
  }

  @Override
  public void onApplicationEvent(ContextRefreshedEvent event) {
    // 应用启动完成后执行预热
    executorService.submit(this::warmupExtensions);
  }

  /** 预热扩展点 */
  public void warmupExtensions() {
    try {
      log.info("开始预热扩展点...");
      long startTime = System.currentTimeMillis();

      // 获取所有扩展点名称
      Set<String> extensionPointNames = extensionRepository.getAllExtensionPointNames();
      log.info("发现 {} 个扩展点", extensionPointNames.size());

      // 为每个扩展点创建一个默认的业务上下文进行预热
      for (String extensionPointName : extensionPointNames) {
        try {
          // 尝试通过类名加载扩展点类
          Class<?> extensionPoint = Class.forName(extensionPointName);
          // 创建默认的业务上下文
          BizContext context = createDefaultBizContext();
          // 调用路由器的warmup方法
          extensionPointRouter.warmup(extensionPoint);
          // 执行一次路由，触发缓存加载
          extensionPointRouter.route(extensionPoint, context);
          log.debug("预热扩展点: {}", extensionPoint.getName());
        } catch (ClassNotFoundException e) {
          log.warn("无法加载扩展点类: {}", extensionPointName, e);
        } catch (Exception e) {
          log.warn("预热扩展点失败: {}", extensionPointName, e);
        }
      }

      long endTime = System.currentTimeMillis();
      log.info("扩展点预热完成，耗时: {}ms", endTime - startTime);
    } catch (Exception e) {
      log.error("扩展点预热失败", e);
    } finally {
      // 关闭线程池
      executorService.shutdown();
      try {
        if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
          executorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        executorService.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * 创建默认的业务上下文
   *
   * @return 业务上下文
   */
  private BizContext createDefaultBizContext() {
    return BizContext.builder().tenant("default").bizCode("default").scenario("default").build();
  }

  /** 手动触发预热 */
  public void triggerWarmup() {
    executorService.submit(this::warmupExtensions);
  }
}
