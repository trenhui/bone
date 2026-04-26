package com.bone.tpa.claim.application.transfer;

import com.bone.metadata.sdk.enums.FieldModelDefine;
import com.bone.tpa.sdk.claim.enums.PersonTypeEnum;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PersonHead {

    /**
     * 属于页面哪个module
     * @return
     */
    FieldModelDefine moduleCode()  ;


    PersonTypeEnum personType();
}
