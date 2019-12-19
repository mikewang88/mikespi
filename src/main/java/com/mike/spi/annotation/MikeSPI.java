package com.mike.spi.annotation;

import java.lang.annotation.*;

/**
 * @Author: MikeWang
 * @Date: 2019/12/19 11:08 AM
 * @Description:
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface MikeSPI {
    /**
     * default extension name
     */
    String value() default "";

}
