package com.bone.engine.extension.studio.config;

import com.bone.engine.extension.studio.model.ExtPointEntity;
import com.bone.engine.extension.studio.model.ExtensionEntity;
import com.bone.engine.extension.studio.repository.ExtPointRepository;
import com.bone.engine.extension.studio.repository.ExtensionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Date;

/**
 * 数据初始化类，在应用启动时加载一些预设的数据
 */
@Component
public class DataInitializer implements ApplicationRunner {

    @Autowired
    private ExtPointRepository extPointRepository;

    @Autowired
    private ExtensionRepository extensionRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 初始化扩展点数据
        if (extPointRepository.count() == 0) {
            initExtPoints();
        }

        // 初始化扩展实现数据
        if (extensionRepository.count() == 0) {
            initExtensions();
        }
    }

    private void initExtPoints() {
        ExtPointEntity extPoint1 = new ExtPointEntity();
        extPoint1.setName("用户认证处理器");
        extPoint1.setDescription("处理用户登录认证的扩展点");
        extPoint1.setInterfaceName("com.bone.engine.extension.user.AuthProcessor");
        extPoint1.setDomain("user");
        extPoint1.setCategory("authentication");
        extPoint1.setType("processor");
        extPoint1.setVersion("1.0.0");
        extPoint1.setEnabled(true);
        extPoint1.setDeprecated(false);
        extPoint1.setCreatedAt(new Date());
        extPoint1.setUpdatedAt(new Date());

        ExtPointEntity extPoint2 = new ExtPointEntity();
        extPoint2.setName("数据校验器");
        extPoint2.setDescription("用于校验数据合法性的扩展点");
        extPoint2.setInterfaceName("com.bone.engine.extension.data.Validator");
        extPoint2.setDomain("data");
        extPoint2.setCategory("validation");
        extPoint2.setType("validator");
        extPoint2.setVersion("1.0.0");
        extPoint2.setEnabled(true);
        extPoint2.setDeprecated(false);
        extPoint2.setCreatedAt(new Date());
        extPoint2.setUpdatedAt(new Date());

        ExtPointEntity extPoint3 = new ExtPointEntity();
        extPoint3.setName("日志格式化器");
        extPoint3.setDescription("格式化日志输出的扩展点");
        extPoint3.setInterfaceName("com.bone.engine.extension.logging.LogFormatter");
        extPoint3.setDomain("logging");
        extPoint3.setCategory("formatting");
        extPoint3.setType("formatter");
        extPoint3.setVersion("1.0.0");
        extPoint3.setEnabled(true);
        extPoint3.setDeprecated(false);
        extPoint3.setCreatedAt(new Date());
        extPoint3.setUpdatedAt(new Date());

        ExtPointEntity extPoint4 = new ExtPointEntity();
        extPoint4.setName("缓存策略");
        extPoint4.setDescription("自定义缓存策略的扩展点");
        extPoint4.setInterfaceName("com.bone.engine.extension.cache.CacheStrategy");
        extPoint4.setDomain("cache");
        extPoint4.setCategory("strategy");
        extPoint4.setType("strategy");
        extPoint4.setVersion("1.0.0");
        extPoint4.setEnabled(true);
        extPoint4.setDeprecated(false);
        extPoint4.setCreatedAt(new Date());
        extPoint4.setUpdatedAt(new Date());

        ExtPointEntity extPoint5 = new ExtPointEntity();
        extPoint5.setName("通知发送器");
        extPoint5.setDescription("发送各类通知的扩展点");
        extPoint5.setInterfaceName("com.bone.engine.extension.notification.NotificationSender");
        extPoint5.setDomain("notification");
        extPoint5.setCategory("communication");
        extPoint5.setType("sender");
        extPoint5.setVersion("1.0.0");
        extPoint5.setEnabled(true);
        extPoint5.setDeprecated(false);
        extPoint5.setCreatedAt(new Date());
        extPoint5.setUpdatedAt(new Date());

        extPointRepository.saveAll(Arrays.asList(extPoint1, extPoint2, extPoint3, extPoint4, extPoint5));
        System.out.println("初始化了5个扩展点");
    }

    private void initExtensions() {
        // 获取第一个扩展点用于关联
        ExtPointEntity extPoint = extPointRepository.findAll().get(0);
        
        ExtensionEntity extension1 = new ExtensionEntity();
        extension1.setExtPointId(extPoint.getId());
        extension1.setName("默认认证处理器");
        extension1.setDescription("基于用户名密码的默认认证实现");
        extension1.setImplementationClassName("com.bone.engine.extension.user.impl.DefaultAuthProcessor");
        extension1.setTenantCode("default");
        extension1.setPriority(100);
        extension1.setConfiguration("{\"maxRetryAttempts\": 5}");
        extension1.setEnabled(true);
        extension1.setStartupPhase("postProcessor");
        extension1.setCreatedAt(new Date());
        extension1.setUpdatedAt(new Date());

        ExtensionEntity extension2 = new ExtensionEntity();
        extension2.setExtPointId(extPoint.getId());
        extension2.setName("LDAP认证处理器");
        extension2.setDescription("基于LDAP的认证实现");
        extension2.setImplementationClassName("com.bone.engine.extension.user.impl.LdapAuthProcessor");
        extension2.setTenantCode("enterprise");
        extension2.setPriority(200);
        extension2.setConfiguration("{\"ldapUrl\": \"ldap://localhost:389\", \"baseDn\": \"dc=example,dc=com\"}");
        extension2.setEnabled(true);
        extension2.setStartupPhase("postProcessor");
        extension2.setCreatedAt(new Date());
        extension2.setUpdatedAt(new Date());

        ExtensionEntity extension3 = new ExtensionEntity();
        extension3.setExtPointId(extPoint.getId());
        extension3.setName("OAuth认证处理器");
        extension3.setDescription("基于OAuth的认证实现");
        extension3.setImplementationClassName("com.bone.engine.extension.user.impl.OAuthAuthProcessor");
        extension3.setTenantCode("default");
        extension3.setPriority(300);
        extension3.setConfiguration("{\"clientId\": \"oauth-client\", \"clientSecret\": \"oauth-secret\", \"authUrl\": \"https://auth.example.com/oauth2\"}");
        extension3.setEnabled(false); // 禁用状态
        extension3.setStartupPhase("postProcessor");
        extension3.setCreatedAt(new Date());
        extension3.setUpdatedAt(new Date());

        extensionRepository.saveAll(Arrays.asList(extension1, extension2, extension3));
        System.out.println("初始化了3个扩展实现");
    }
}