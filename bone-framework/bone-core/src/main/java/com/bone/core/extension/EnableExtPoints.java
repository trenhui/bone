package com.bone.core.extension;

import com.bone.core.extension.register.ExtPointRegister;
import com.bone.core.extension.repository.MemExtPointRepository;
import com.bone.core.extension.route.DefaultExtPointRouter;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * EnableExtPoints
 *
 * @author renhui.trh 2023-10-30
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Documented
@Import({ExtPointRegister.class})
public @interface EnableExtPoints {
    String[] basePackages() default {};

    Class<?> extPointRepository() default MemExtPointRepository.class;

    Class<?> extPointRouter() default DefaultExtPointRouter.class;
}
