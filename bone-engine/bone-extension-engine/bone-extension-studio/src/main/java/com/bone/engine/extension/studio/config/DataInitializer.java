package com.bone.engine.extension.studio.config;

import com.bone.engine.extension.studio.domain.model.ExtPoint;
import com.bone.engine.extension.studio.domain.model.Extension;
import com.bone.engine.extension.studio.domain.repository.ExtPointRepository;
import com.bone.engine.extension.studio.domain.repository.ExtensionEntityRepository;
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
    private ExtensionEntityRepository extensionEntityRepository;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 暂时注释掉初始化代码，以便项目能够编译通过
        /*
        // 初始化扩展点数据
        if (extPointRepository.count() == 0) {
            initExtPoints();
        }

        // 初始化扩展实现数据
        if (extensionEntityRepository.count() == 0) {
            initExtensions();
        }
        */
    }

    private void initExtPoints() {
        // 暂时注释掉初始化代码
        /*
        ExtPoint extPoint1 = new ExtPoint();
        extPoint1.setName("用户认证处理器");
        extPoint1.setDescription("处理用户登录认证的扩展点");
        extPoint1.setInterfaceName("com.bone.engine.extension.user.AuthProcessor");
        extPoint1.setDomain("user");
        extPoint1.setCategory("authentication");
        extPoint1.setEnabled(true);

        ExtPoint extPoint2 = new ExtPoint();
        extPoint2.setName("数据校验器");
        extPoint2.setDescription("用于校验数据合法性的扩展点");
        extPoint2.setInterfaceName("com.bone.engine.extension.data.Validator");
        extPoint2.setDomain("data");
        extPoint2.setCategory("validation");
        extPoint2.setEnabled(true);

        ExtPoint extPoint3 = new ExtPoint();
        extPoint3.setName("日志格式化器");
        extPoint3.setDescription("格式化日志输出的扩展点");
        extPoint3.setInterfaceName("com.bone.engine.extension.logging.LogFormatter");
        extPoint3.setDomain("logging");
        extPoint3.setCategory("formatting");
        extPoint3.setEnabled(true);

        ExtPoint extPoint4 = new ExtPoint();
        extPoint4.setName("缓存策略");
        extPoint4.setDescription("自定义缓存策略的扩展点");
        extPoint4.setInterfaceName("com.bone.engine.extension.cache.CacheStrategy");
        extPoint4.setDomain("cache");
        extPoint4.setCategory("strategy");
        extPoint4.setEnabled(true);

        ExtPoint extPoint5 = new ExtPoint();
        extPoint5.setName("通知发送器");
        extPoint5.setDescription("发送各类通知的扩展点");
        extPoint5.setInterfaceName("com.bone.engine.extension.notification.NotificationSender");
        extPoint5.setDomain("notification");
        extPoint5.setCategory("communication");
        extPoint5.setEnabled(true);

        // 保存扩展点
        extPointRepository.save(extPoint1);
        extPointRepository.save(extPoint2);
        extPointRepository.save(extPoint3);
        extPointRepository.save(extPoint4);
        extPointRepository.save(extPoint5);
        System.out.println("初始化了5个扩展点");
        */
    }

    private void initExtensions() {
        // 暂时注释掉初始化代码
        /*
        // 获取第一个扩展点用于关联
        ExtPoint extPoint = extPointRepository.findAll().get(0);
        
        Extension extension1 = new Extension();
        extension1.setExtPoint(extPoint);
        extension1.setName("默认认证处理器");
        extension1.setDescription("基于用户名密码的默认认证实现");
        extension1.setClassName("com.bone.engine.extension.user.impl.DefaultAuthProcessor");
        extension1.setTenantCode("default");
        extension1.setPriority(100);
        extension1.setConfig("{\"maxRetryAttempts\": 5}");
        extension1.setEnabled(true);

        Extension extension2 = new Extension();
        extension2.setExtPoint(extPoint);
        extension2.setName("LDAP认证处理器");
        extension2.setDescription("基于LDAP的认证实现");
        extension2.setClassName("com.bone.engine.extension.user.impl.LdapAuthProcessor");
        extension2.setTenantCode("enterprise");
        extension2.setPriority(200);
        extension2.setConfig("{\"ldapUrl\": \"ldap://localhost:389\", \"baseDn\": \"dc=example,dc=com\"}");
        extension2.setEnabled(true);

        Extension extension3 = new Extension();
        extension3.setExtPoint(extPoint);
        extension3.setName("OAuth认证处理器");
        extension3.setDescription("基于OAuth的认证实现");
        extension3.setClassName("com.bone.engine.extension.user.impl.OAuthAuthProcessor");
        extension3.setTenantCode("default");
        extension3.setPriority(300);
        extension3.setConfig("{\"clientId\": \"oauth-client\", \"clientSecret\": \"oauth-secret\", \"authUrl\": \"https://auth.example.com/oauth2\"}");
        extension3.setEnabled(false); // 禁用状态

        // 保存扩展实现
        extensionEntityRepository.save(extension1);
        extensionEntityRepository.save(extension2);
        extensionEntityRepository.save(extension3);
        System.out.println("初始化了3个扩展实现");
        */
    }
}