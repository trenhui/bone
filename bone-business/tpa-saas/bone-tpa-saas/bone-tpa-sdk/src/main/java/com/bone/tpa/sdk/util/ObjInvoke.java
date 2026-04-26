package com.bone.tpa.sdk.util;

import org.springframework.data.annotation.Transient;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;

/**
 * @description:
 * @author: caixuepu
 * @time: 2021/5/25
 */
public class ObjInvoke {
    /**
     * 设置bigdimcail 数字为0
     * @param obj
     */
    public static  void getObjBigDemical(Object obj) {
        if( obj== null){
            return;
        }
        // 得到类对象
        Class objCla = obj.getClass();
        Field[] fs = objCla.getDeclaredFields();
        for (int i = 0; i < fs.length; i++) {
            Field f = fs[i];
            Transient annot  =  f.getAnnotation(Transient.class);
            if(annot != null){
                continue;
            }
            // 设置些属性是可以访问的
            boolean isStatic = Modifier.isStatic(f.getModifiers());
            if (isStatic) {
                continue;
            }
            // 设置些属性是可以访问的
            f.setAccessible(true);
            try {
                // 得到此属性的值
                Object val = f.get(obj);
                // 得到此属性的类型
                String type = f.getType().toString();
                if (type.endsWith("BigDecimal") && val == null) {
                    f.set(obj, new BigDecimal(0));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
