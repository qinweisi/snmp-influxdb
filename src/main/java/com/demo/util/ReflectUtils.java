package com.demo.util;

import org.nutz.lang.Lang;

import java.lang.reflect.Field;

/**
 * 反射工具类
 *
 **/
public class ReflectUtils {

    /**
     * 类中获取使用了某个注解的字段
     *
     * @param object
     * @param annotation
     */
    public static String getField(Object object, Class annotation) {
        Object first = Lang.first(object);
        Class clazz = first.getClass();
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            Boolean isAnon = field.isAnnotationPresent(annotation);
            if (isAnon) {
                return field.getName();
            }
        }
        return null;
    }
}
