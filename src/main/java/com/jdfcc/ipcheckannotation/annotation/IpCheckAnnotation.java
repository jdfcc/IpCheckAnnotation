package com.jdfcc.ipcheckannotation.annotation;

import java.lang.annotation.*;

/**
 * @author Jdfcc
 * @Description 接口防刷
 * @DateTime 2023/6/28 10:22
 */

@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface IpCheckAnnotation {

    /**
     * 访问时间间隔，单位为秒 s
     */
    int time() default 10;

    /**
     * 单位时间内ip允许访问次数
     */
    int count() default 10;

}

