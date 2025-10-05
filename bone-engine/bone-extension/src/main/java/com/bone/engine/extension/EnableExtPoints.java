package com.bone.engine.extension;

import com.bone.engine.extension.register.ExtPointRegister;
import com.bone.engine.extension.repository.MemExtPointRepository;
import com.bone.engine.extension.route.DefaultExtPointRouter;
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
